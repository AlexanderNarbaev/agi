package io.matrix;

import io.matrix.cli.SimulateCommand;
import io.matrix.cli.EvolutionCommand;
import io.matrix.cli.DemoCommand;
import picocli.CommandLine.Command;

@Command(name = "matrix", mixinStandardHelpOptions = true,
        subcommands = {SimulateCommand.class, EvolutionCommand.class, DemoCommand.class},
        description = "MATRIX Cognitive Architecture CLI")
public class MatrixTopCommand {}
