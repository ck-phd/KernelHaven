/*
 * Copyright 2020 University of Hildesheim, Software Systems Engineering
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
package net.ssehub.kernel_haven.incremental;

import net.ssehub.comani.analysis.AnalysisResult;
import net.ssehub.kernel_haven.PipelineConfigurator;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.net.IServerTask;
import net.ssehub.kernel_haven.net.NetException;
import net.ssehub.kernel_haven.net.Server;
import net.ssehub.kernel_haven.util.Logger;

/**
 * This class wraps the original {@link PipelineConfigurator} to split its preparation and the actual execution of an
 * analysis into two separate steps. An instance of this class is used when executing KernelHaven as a server, which
 * results in a single preparation call at server start-up and recurring analysis calls for each client message received
 * by the server. That client message is interpreted as a commit, which will be pre-processed in order to identify those
 * artifacts affected by the introduced changes. The analysis only considers those affected artifacts.
 *
 * @author Christian Kroeher
 */
public class IncrementalPipelineConfigurator implements IServerTask {
    
    /**
     * The logger for logging messages.
     */
    private static final Logger LOGGER = Logger.get();

    /**
     * The original KernelHaven pipeline configurator wrapped by this class.
     */
    private static final PipelineConfigurator WRAPPED_CONFIGURATOR = PipelineConfigurator.instance();
    
    /**
     * The wrapper of the Commit Analysis Infrastructure (ComAnI) for parsing a given commit (string) and identifying
     * its changes relevant for the desired KernelHaven analysis.
     */
    private static final ComAnI WRAPPED_COMANI = ComAnI.instance();
    
    /**
     * The singleton instance of this class.
     */
    private static IncrementalPipelineConfigurator instance;
    
    /**
     * The current configuration of KernelHaven as specified by the user-defined properties file passed as command line
     * argument.
     */
    private static Configuration configuration;
    
    /**
     * Constructs the singleton instance of this class.
     * 
     * @param serverNetworkAddress the network address of the internal server; that string must either be
     *        <code>null</code> to run the internal server as <i>localhost</i> or of the form "SERVER_IP::SERVER_PORT"
     *        to run the internal server with that address
     * @throws SetUpException if preparing the {@link #WRAPPED_CONFIGURATOR} or starting the internal server fails
     */
    private IncrementalPipelineConfigurator(String serverNetworkAddress) throws SetUpException {
        prepare();        
        try {            
            if (serverNetworkAddress != null) {
                // use custom server IP and port
                Server.start(serverNetworkAddress, this);
            } else {
                // run server as localhost
                Server.start(this);
            }
        } catch (NetException e) {
            throw new SetUpException("Starting the server failed", e);
        }
    }
    
    /**
     * Prepares the incremental infrastructure by loading the {@link #WRAPPED_COMANI} infrastructure and setting up the
     * {@link #WRAPPED_CONFIGURATOR}.
     * 
     * @throws SetUpException if preparation fails
     */
    private void prepare() throws SetUpException {
        WRAPPED_COMANI.loadInfrastructure(configuration);
        WRAPPED_CONFIGURATOR.loadPlugins();
        WRAPPED_CONFIGURATOR.instantiateExtractors();
        WRAPPED_CONFIGURATOR.createProviders();
        WRAPPED_CONFIGURATOR.runPreparation(); // TODO questionable if preparation is used in incremental instances
        WRAPPED_CONFIGURATOR.instantiateAnalysis();
    }
    
    @Override
    public void execute(String input) {
        /*
         * Note that the general PipelineConfigurator calls archive() after runAnalysis() during its execution. For now,
         * there is no need to archive any information in an incremental setup. However, this may change in future.
         */
        
        /*
         * TODO this execution depends on the changes a received commit introduces. Hence, simply calling "runAnalysis"
         * does not work, but we need to prepare the relevant extractors and (maybe) modify the analysis depending on
         * the introduced changes. The result should be that the analysis only receives the artifacts affected by
         * changes and that the extractors only re-extract if necessary; in other cases either the extractors or
         * some other instance(s) must provide the unchanged artifacts (as the hybrid cache does). 
         */
        try {
            /*
             * TODO avoid using AnalysisResult here directly as it belongs to ComAnI.
             * Maybe individual getters for each type of artifacts indicating whether such artifacts changed by the
             * current commit.
             */
            AnalysisResult analysisResult = WRAPPED_COMANI.analyze(input);
            LOGGER.logInfo("ComAnI analysis results: " + analysisResult);
        } catch (IncrementalException e) {
            LOGGER.logException("Incremental analysis failed", e);
        }
        
        WRAPPED_CONFIGURATOR.runAnalysis();
        
    }

    @Override
    public String getExecutionSummary() {
        // TODO Return the result of the incremental analysis or at least the path to the saved results (file).
        return "TODO: analysis result will be provided soon";
    }
    
    /**
     * Creates the singleton instance of this {@link IncrementalPipelineConfigurator} and starts its internal server
     * with the given server network address and the given configuration to perform incremental analyses based on
     * received client messages.
     *  
     * @param serverNetworkAddress the network address of the internal server; that string must either be
     *        <code>null</code> to run the internal server as <i>localhost</i> or of the form "SERVER_IP::SERVER_PORT"
     *        to run the internal server with that address
     * @param config the current configuration of KernelHaven as specified by the user-defined properties file
     *        passed as command line argument
     */
    public static void run(String serverNetworkAddress, Configuration config) {
        if (instance == null) {            
            try {
                configuration = config; 
                instance = new IncrementalPipelineConfigurator(serverNetworkAddress);
            } catch (SetUpException e) {
                LOGGER.logException("Error while setting up incremental pipeline", e);
            }
        } else {
            LOGGER.logWarning("Incremental pipeline configurator already running");
        }
    }

}
