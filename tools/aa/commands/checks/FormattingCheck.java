/*
 * Copyright 2018 The StartupOS Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.startupos.tools.aa.commands.checks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import com.google.startupos.tools.formatter.FormatterTool;
import javax.inject.Inject;
import javax.inject.Named;

import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.lang.String;

public class FormattingCheck implements FixCommandCheck {

  private String workspacePath;

  @Inject
  public FormattingCheck(@Named("Workspace path") String wsPath) {
    this.workspacePath = wsPath;
  }

  @Override
  public boolean perform() {

    String ignoredNodeModulesDirs;

    try {
      ignoredNodeModulesDirs =
          String.join(
              ",",
              Files.walk(Paths.get(""))
                  .filter(Files::isDirectory)
                  .filter(p -> p.getFileName().toString().equals("node_modules"))
                  .map(p -> p.toAbsolutePath().toString())
                  .collect(Collectors.toList()));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }

    System.err.println("curdir: " + this.workspacePath);

    FormatterTool.main(
        new String[] {
          "--path",
          this.workspacePath,
          "--java",
          "--python",
          "--proto",
          "--cpp",
          "--build",
          "--ignore_directories",
          ignoredNodeModulesDirs
        });
    return true;
  }

  @Override
  public String name() {
    return "formatting";
  }
}

