// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;

public class TransformMavenRunner {

    private MavenRunner mavenRunner;
    private Project project;

    public TransformMavenRunner(MavenRunner mavenRunner, final Project project) {
        this.mavenRunner = mavenRunner;
        this.project = project;
    }


    public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final TransformRunnable onComplete) {
        FileDocumentManager.getInstance().saveAllDocuments();

        ProgramRunner.Callback callback = descriptor -> {
            ProcessHandler handler = descriptor.getProcessHandler();
            if (handler == null) return;
            handler.addProcessListener(new ProcessAdapter() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
//                    if (event.getExitCode() == 0 && onComplete != null) {
//                        System.out.println("xishen in runner: " + onComplete.isComplete());
//                        onComplete.run();
//                    } else if (onComplete != null){
//                        System.out.println("xishen in runner: " + onComplete.isComplete());
//                        onComplete.stop();
//                        System.out.println("xishen in runner: " + onComplete.isComplete());
//
//                    }
                    if (onComplete != null) {
                        System.out.println("xishen in runner number: " + onComplete.isComplete());
                        onComplete.exitCode(event.getExitCode());
                        System.out.println("xishen in runner number after: " + onComplete.isComplete());
                    }
                }
            });
        };

        MavenRunConfigurationType.runConfiguration(project, parameters, null, settings, callback, false);
    }
}

