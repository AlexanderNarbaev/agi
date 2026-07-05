package io.matrix;

import io.matrix.cli.SimulateCommand;
import io.matrix.cli.EvolutionCommand;
import io.matrix.cli.DemoCommand;
import io.matrix.cli.GridWorldPilotCommand;
import io.matrix.cli.RobotArmCommand;
import io.matrix.cli.CauldronDemoCommand;
import io.matrix.cli.HadesDemoCommand;
import io.matrix.cli.NoosphereDemoCommand;
import io.matrix.cli.PipelineCommand;
import picocli.CommandLine.Command;

@Command(name = "matrix", mixinStandardHelpOptions = true,
        subcommands = {SimulateCommand.class, EvolutionCommand.class, DemoCommand.class,
                       GridWorldPilotCommand.class, RobotArmCommand.class,
                       CauldronDemoCommand.class, HadesDemoCommand.class,
                       NoosphereDemoCommand.class, PipelineCommand.class},
        description = "MATRIX Cognitive Architecture CLI")
public class MatrixTopCommand {}
