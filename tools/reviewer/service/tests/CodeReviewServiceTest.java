package com.google.startupos.tools.reviewer.service.tests;

import com.google.startupos.common.CommonModule;
import com.google.startupos.common.FileUtils;
import com.google.startupos.common.TextDifferencer;
import com.google.startupos.common.flags.Flags;
import com.google.startupos.common.repo.GitRepo;
import com.google.startupos.common.repo.GitRepoFactory;
import com.google.startupos.tools.aa.AaModule;
import com.google.startupos.tools.aa.commands.InitCommand;
import com.google.startupos.tools.aa.commands.WorkspaceCommand;
import com.google.startupos.tools.localserver.service.AuthService;
import com.google.startupos.tools.reviewer.service.CodeReviewService;
import com.google.startupos.tools.reviewer.service.CodeReviewServiceGrpc;
import dagger.Component;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.After;
import org.junit.Before;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public abstract class CodeReviewServiceTest {

  static final String REPO_ID = "startup-os";
  static final String TEST_FILE = "test_file.txt";
  static final String TEST_FILE_CONTENTS = "Some test file contents\n";
  static final String FILE_IN_HEAD = "im_in_head.txt";
  static final String TEST_WORKSPACE = "ws1";
  static final String COMMIT_MESSAGE = "Some commit message";

  private ManagedChannel channel;
  private String aaBaseFolder;
  private Server server;
  GitRepoFactory gitRepoFactory;
  String testFileCommitId;
  String fileInHeadCommitId;
  GitRepo repo;
  FileUtils fileUtils;
  TestComponent component;
  CodeReviewServiceGrpc.CodeReviewServiceBlockingStub blockingStub;

  @Before
  public void setup() throws IOException {
    Flags.parse(
        new String[0], AuthService.class.getPackage(), CodeReviewService.class.getPackage());
    String testFolder = Files.createTempDirectory("temp").toAbsolutePath().toString();
    String initialRepoFolder = joinPaths(testFolder, "initial_repo");
    aaBaseFolder = joinPaths(testFolder, "base_folder");

    component =
        DaggerCodeReviewServiceTest_TestComponent.builder()
            .aaModule(
                new AaModule() {
                  @Provides
                  @Singleton
                  @Override
                  @Named("Base path")
                  public String provideBasePath(FileUtils fileUtils) {
                    return aaBaseFolder;
                  }
                })
            .build();
    gitRepoFactory = component.getFactory();
    fileUtils = component.getFileUtils();

    createInitialRepo(initialRepoFolder);
    initAaBase(initialRepoFolder, aaBaseFolder);
    createAaWorkspace(TEST_WORKSPACE);
    createBlockingStub();
    writeFile(TEST_FILE_CONTENTS);
    testFileCommitId = repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE).getId();
  }

  @After
  public void after() throws InterruptedException {
    server.shutdownNow();
    server.awaitTermination();
    channel.shutdownNow();
    channel.awaitTermination(1, TimeUnit.SECONDS);
  }

  private void createInitialRepo(String initialRepoFolder) {
    fileUtils.mkdirs(initialRepoFolder);
    GitRepo repo = gitRepoFactory.create(initialRepoFolder);
    repo.init();
    repo.setFakeUsersData();
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinPaths(initialRepoFolder, FILE_IN_HEAD));
    fileInHeadCommitId = repo.commit(repo.getUncommittedFiles(), "Initial commit").getId();
  }

  private void initAaBase(String initialRepoFolder, String aaBaseFolder) {
    InitCommand initCommand = component.getInitCommand();
    InitCommand.basePath.resetValueForTesting();
    InitCommand.startuposRepo.resetValueForTesting();
    String[] args = {
      "--startupos_repo", initialRepoFolder,
      "--base_path", aaBaseFolder,
    };
    initCommand.run(args);
  }

  private void createAaWorkspace(String name) {
    WorkspaceCommand workspaceCommand = component.getWorkspaceCommand();
    String[] args = {"workspace", "-f", name};
    workspaceCommand.run(args);
    String repoPath = fileUtils.joinPaths(getWorkspaceFolder(TEST_WORKSPACE), "startup-os");
    repo = gitRepoFactory.create(repoPath);
    repo.setFakeUsersData();
  }

  private void createBlockingStub() throws IOException {
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(component.getCodeReviewService())
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    blockingStub = CodeReviewServiceGrpc.newBlockingStub(channel);
  }

  private String joinPaths(String first, String... more) {
    return FileSystems.getDefault().getPath(first, more).toAbsolutePath().toString();
  }

  String getWorkspaceFolder(String workspace) {
    return joinPaths(aaBaseFolder, "ws", workspace);
  }

  void writeFile(String contents) {
    writeFile(TEST_FILE, contents);
  }

  void writeFile(String filename, String contents) {
    fileUtils.writeStringUnchecked(
        contents, fileUtils.joinPaths(getWorkspaceFolder(TEST_WORKSPACE), "startup-os", filename));
  }

  void deleteFile(String filename) {
    fileUtils.deleteFileOrDirectoryIfExistsUnchecked(
        fileUtils.joinPaths(getWorkspaceFolder(TEST_WORKSPACE), "startup-os", filename));
  }

  @Singleton
  @Component(modules = {CommonModule.class, AaModule.class})
  interface TestComponent {
    CodeReviewService getCodeReviewService();

    GitRepoFactory getFactory();

    InitCommand getInitCommand();

    WorkspaceCommand getWorkspaceCommand();

    FileUtils getFileUtils();

    TextDifferencer getTextDifferencer();
  }
}

