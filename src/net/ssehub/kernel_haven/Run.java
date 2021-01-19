/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.incremental.IncrementalPipelineConfigurator;
import net.ssehub.kernel_haven.net.Client;
import net.ssehub.kernel_haven.net.NetException;
import net.ssehub.kernel_haven.net.Server;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;

/**
 * 
 * Class for loading a configuration and starting the PipelineConfigurator.
 * 
 * KernelHaven can be executed in three different variants:
 * <ul>
 *     <li><i>Classic</i>: usual execution</li>
 *     <li><i>Client</i>: starting a client only to send the second command line argument value as commit to the
 *            server</li>
 *     <li><i>Server</i>: incremental execution processing each message of a client as commit and analyzing affected
 *            artifacts only</li>
 * </ul>
 * 
 * @author Adam
 * @author Christian Kroeher
 *
 */
public class Run {
    
    /**
     * Enumeration of the three execution variants supported by KernelHaven.
     *
     * @author Christian Kroeher
     */
    private static enum ExecutionVariant { CLASSIC, CLIENT, SERVER };

    private static final Logger LOGGER = Logger.get();
    
    private static File propertiesFile = null;
    
    private static Configuration config = null; // TODO use regex for file identification in incremental configurator?

    /**
     * Prints some information about the system to the log.
     */
    private static void printSystemInfo() {
        Properties properties = System.getProperties();
        
        List<String> lines = new LinkedList<>();
        lines.add("System Info:");
        
        // try to get the KernelHaven version from version.txt in the jar file
        // this only works properly if the class path only has the kernelhaven.jar
        String version = null;
        try (InputStream versionFile = ClassLoader.getSystemResourceAsStream("version.txt")) {
            if (versionFile != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(versionFile));
                version = in.readLine();
            }
        } catch (IOException e) {
            // ignore
        }
        if (version != null) {
            lines.add("\tKernelHaven Version: " + version);
        }
        
        String[][] relevantKeys = {
            // key, visible name
            {"os.name", "OS Name"},
            {"os.version", "OS Version"},
            {"os.arch", "OS Arch"},
            {"java.home", "Java Home"},
            {"java.vendor", "Java Vendor"},
            {"java.version", "Java Version"},
            {"java.vm.name", "Java VM Name"},
            {"java.vm.version", "Java VM Version"},
            {"java.vm.vendor", "Java VM Vendor"},
            {"user.dir", "Working Directory"},
            {"java.io.tmpdir", "Temporary Directory"},
            {"file.encoding", "File Encoding"},
            {"java.class.path", "Java Class Path"},
        };
        
        for (String[] key : relevantKeys) {
            if (properties.containsKey(key[0])) {
                lines.add("\t" + key[1] + ": " + properties.get(key[0]));
            }
        }
        
        Runtime runtime = Runtime.getRuntime();
        lines.add("\tMaximum Memory: " + Util.formatBytes(runtime.maxMemory()));
        lines.add("\tAvailable Processors: " + runtime.availableProcessors());
        
        LOGGER.logInfo(notNull(lines.toArray(new String[0])));
    }
    
    /**
     * Determines the desired KernelHaven variant to execute. That variant depends on the first command line argument
     * in the given set of arguments:
     * <ul>
     *     <li>If the first argument starts with "--client", the desired execution variant is
     *         {@link ExecutionVariant#CLIENT}</li>
     *     <li>If the first argument starts with "--server", the desired execution variant is
     *         {@link ExecutionVariant#SERVER}</li>
     *     <li>In all other cases, the desired execution variant is {@link ExecutionVariant#CLASSIC}</li>
     * </ul>
     * 
     * @param args the set of command line arguments; must not be <code>null</code>
     * @return the desired {@link ExecutionVariant}; never <code>null</code>
     */
    private static ExecutionVariant getExecutionVariant(String... args) {
        ExecutionVariant variant = ExecutionVariant.CLASSIC;
        if (args.length > 0) {
            String firstArg = args[0];
            // Client flag must and server flag may have an additional value specifying the server IP and port
            if (firstArg.startsWith("--client")) {
                variant = ExecutionVariant.CLIENT;
            } else if (firstArg.startsWith("--server")) {
                variant = ExecutionVariant.SERVER;
            }
        }
        return variant;
    }

    /**
     * Main method to execute the Pipeline defined in the the properties file.
     *
     * 
     * @param args
     *            the command line arguments. Used for parsing property-file
     *            location and flags.
     * @return Whether run was successful or not.
     */
    // CHECKSTYLE:OFF // ignore "too many returns" error
    public static boolean run(String... args) {
    // CHECKSTYLE:ON
        if (!setup(args)) {
            return false;
        }
        
        LOGGER.logInfo("Start executing KernelHaven with configuration file " + propertiesFile.getPath());
        printSystemInfo();

        PipelineConfigurator.instance().execute();
        return true;
    }
    
    /**
     * Main method to execute the KernelHaven client.
     *
     * 
     * @param args the command line arguments. These arguments must be of the following form:
     *        args[0] == "--client=SERVER_IP::SERVER_PORT", args[1] == "COMMIT_CONTENT"
     * @return <code>true</code>, if executing the client was successful; <code>false</code> otherwise
     */
    public static boolean runClient(String... args) {
        Thread.currentThread().setName("Client");
        boolean success = false;
        /*
         * Expected args: args[0] == "--client=SERVER_IP::SERVER_PORT", args[1] == "COMMIT_CONTENT"
         * If this method is called, args[0] at least holds "--client" (see main method)
         */
        if (args.length == 2) {
            try {
                LOGGER.logInfo("Start executing KernelHaven client");
                Client client = Client.connect(getFlagValue(args[0]));
                LOGGER.logInfo("Client sends: " + args[1]);
                String serverAnswer = client.send(args[1]);
                LOGGER.logInfo("Client receives: " + serverAnswer);
                client.close();
            } catch (NetException e) {
                LOGGER.logException("Executing KernelHaven client failed", e);
            }
        } else {
            LOGGER.logError("Wrong number of arguments.",
                    "Execution of KernelHaven client expects the following two arguments:",
                    "args[0] == \"--client=SERVER_IP::SERVER_PORT\", args[1] == \"COMMIT_CONTENT\"");
        }
        return success;
    }
    
    /**
     * Main method to execute the KernelHaven server. That server processes each message of a client as commit and
     * incrementally analyzes affected artifacts only until a client sends the {@link Server#SHUT_DOWN_COMMAND}.<br>
     * <br>
     * The given command line arguments must be of the following form:
     * <ul>
     *     <li>The first argument starts with "--server" and may have an optional flag value of the form
     *         "SERVER_IP::SERVER_PORT" to specify the KernelHaven server IP and port number to use. If that flag value
     *         is not available, the server runs as <i>localhost</i> equivalent to "--server=127.0.0.1::3141"</li>
     *     <li>The remaining arguments define the usual KernelHaven parameters as for its classic execution variant</li>
     * </ul>
     * 
     * @param args the command line arguments as described above
     * @return <code>true</code>, if starting the server was successful; <code>false</code> otherwise
     */
    public static boolean runServer(String... args) {
        boolean isRunning = false;
        // If this method is called, args[0] at least holds "--server" (see main method)
        String serverFlag = args[0];
        String serverNetworkAddress = getFlagValue(serverFlag);
        
        // Remove server flag from args
        String[] reducedArgs = new String[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            reducedArgs[i - 1] = args[i];
        }
        
        // Call the typical KernelHaven setup with reduced args (no server flag included)
        if (setup(reducedArgs)) {
            // Start the IncrementalPipelineConfigurator and its internal server
            LOGGER.logInfo("Start executing incremental KernelHaven with configuration file "
                    + propertiesFile.getPath());
            printSystemInfo();
            IncrementalPipelineConfigurator.run(serverNetworkAddress);
            isRunning = true;
        }
        
        return isRunning;
    }
    
    /**
     * Determines the additional value of the "--client" and "--server" flag. That flag may be given as part of the
     * command line arguments to start the respective KernelHaven execution variant. For the client variant, specifying
     * the server IP and port as additional flag value is mandatory for establishing a client connection. For the server
     * variant, a custom IP and port may be given at which the server will be available. 
     * 
     * @param flag the comprehensive execution variant flag as specified by the first command line argument, e.g.
     *        "--client=SERVER_IP::SERVER_PORT", "--server", or "server=SERVER_IP::SERVER_PORT"
     * @return the additional flag value "SERVER_IP::SERVER_PORT" or <code>null</code>, if that value is not available
     */
    private static String getFlagValue(String flag) {
        // flag is either "--client=SERVER_IP::SERVER_PORT", "--server", or "server=SERVER_IP::SERVER_PORT"
        String flagValue = null;
        if (flag != null && !flag.isBlank()) {
            String[] splittedFlag = flag.split("=");
            if (splittedFlag.length == 2) {
                flagValue = splittedFlag[1];
            }
        }
        return flagValue;
    }
    
    /**
     * Prepares KernelHaven and its setup based on the given command line arguments.
     * 
     * @param args the command line arguments used for parsing property-file location and flags. Must not be
     *        <code>null</code>
     * @return <code>true</code>, if setup was successful; <code>false</code> otherwise
     */
    // CHECKSTYLE:OFF // ignore "too many returns" error
    private static boolean setup(String... args) {
    // CHECKSTYLE:ON
        Thread.currentThread().setName("Setup");
        
        Thread.setDefaultUncaughtExceptionHandler((Thread thread, Throwable exc) -> {
            LOGGER.logException("Unhandled exception in thread " + thread.getName(), exc);
        });

        File propertiesFile = null;

        boolean archiveParam = false;

        for (String arg : args) {
            if (arg.equals("--archive")) {
                archiveParam = true;
            } else if (!arg.startsWith("--")) {
                if (propertiesFile == null) {
                    propertiesFile = new File(arg);
                } else {
                    LOGGER.logError("You must not define more than one properties file");
                    return false;
                }

            } else {
                LOGGER.logError("Unknown command line option " + arg);
                return false;
            }
        }

        if (propertiesFile == null) {
            LOGGER.logError("No properties-file provided. Stopping system");
            return false;
        }

        Configuration config = null;

        try {
            config = new Configuration(propertiesFile);
            DefaultSettings.registerAllSettings(config);

            if (archiveParam) {
                config.setValue(DefaultSettings.ARCHIVE, true);
            }

            PipelineConfigurator.instance().init(config);

        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            return false;
        }
        try {
            LOGGER.setup(config);

        } catch (SetUpException exc) {
            LOGGER.logException(
                    "Was not able to setup the Logger as defined in the properties. Logging now to Console only", exc);
        }
        
        Run.propertiesFile = propertiesFile;
        Run.config = config;
        return true;
    }
    
    /**
     * Main method to execute KernelHaven (non-)incrementally as defined in the the properties file.
     *
     * 
     * @param args
     *            the command line arguments. Used for parsing property-file
     *            location and flags.
     */
    public static void main(String... args) {
        ExecutionVariant variant = getExecutionVariant(args);
        
        boolean success;
        switch(variant) {
        case CLASSIC:
            success = run(args);
            break;
        case CLIENT:
            success = runClient(args);
            break;
        case SERVER:
            success = runServer(args);
            break;
        default:
            // Should never be reached
            LOGGER.logError("Unknown execution variant \"", variant.name(), "\"");
            success = false;
            break;
        }
        
        if (!success) {
            System.exit(1);
        }
    }
    
}
