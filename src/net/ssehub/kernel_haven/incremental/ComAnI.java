/*
 * Copyright 2021 University of Hildesheim, Software Systems Engineering
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

import java.io.File;
import java.util.Properties;

import net.ssehub.comani.analysis.AbstractCommitAnalyzer;
import net.ssehub.comani.analysis.AnalysisResult;
import net.ssehub.comani.core.Setup;
import net.ssehub.comani.core.SetupException;
import net.ssehub.comani.data.Commit;
import net.ssehub.comani.data.CommitQueue;
import net.ssehub.comani.data.CommitQueue.QueueState;
import net.ssehub.comani.extraction.AbstractCommitExtractor;
import net.ssehub.comani.utility.InfrastructureUtilities;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.util.Logger;

/**
 * This class wraps the Commit Analysis Infrastructure (ComAnI) for parsing a given commit (string) and identifying its
 * changes relevant for the desired KernelHaven analysis.
 *
 * @author Christian Kroeher
 */
public class ComAnI {
    
    /**
     * The logger for logging messages.
     */
    private static final Logger LOGGER = Logger.get();

    /**
     * The ComAnI utilities used to load the desired commit extractor and analyzer plug-ins.
     */
    private static final InfrastructureUtilities COMANI_UTILITIES = InfrastructureUtilities.getInstance();
    
    /**
     * The singleton instance of this class.
     */
    private static ComAnI instance = new ComAnI();
    
    /**
     * The commit queue connecting the commit extractor and the commit analyzer of ComAnI. The commit extractor requires
     * this queue as target to push its extracted commit information to. The commit analyzer requires this queue as
     * source to receive commit information to analyze.
     */
    private CommitQueue commitQueue;
    
    /**
     * The commit extractor instance of ComAnI. This instance is responsible for extracting the commit information from
     * a given commit string.
     */
    private AbstractCommitExtractor commitExtractor;
    
    /**
     * The commit analyzer instance of ComAnI. This instance is responsible for analyzing the commit information
     * regarding changes relevant for the current KernelHaven analysis.
     */
    private AbstractCommitAnalyzer commitAnalyzer;
    
    /**
     * The flag indicating whether {@link #loadInfrastructure(Configuration)} was called successfully and the 
     * {@link #commitQueue}, the {@link #commitExtractor}, and the {@link #commitAnalyzer} are all not
     * <code>null</code>. In that case, this flag holds the value <code>true</code>. In all other cases, this flag holds
     * the value <code>false</code>, which is also the default value.
     */
    private boolean infrastructureLoaded;

    /**
     * Constructs the singleton instance of this class. That instance requires initialization via 
     * {@link #loadInfrastructure(Configuration)} before {@link #analyze(String)} can be called successfully. 
     */
    private ComAnI() {
        infrastructureLoaded = false;
    }
    
    /**
     * Loads the Commit Analysis Infrastructure (ComAnI) based on the definitions in the properties file specified by
     * the {@link DefaultSettings#COMANI_PROPERTIES_FILE_PATH} in the given configuration. In particular, this method
     * instantiates the desired commit extractor and commit analyzer used to process a given commit (string).
     * 
     * @param configuration the current configuration of KernelHaven as specified by the user-defined properties file
     *        passed as command line argument 
     * @throws SetUpException if {@link DefaultSettings#COMANI_PROPERTIES_FILE_PATH} is <code>null</code>, creating the
     *         ComAnI setup fails, or the loading the ComAnI plug-ins fails
     */
    @SuppressWarnings("null") // The default value of settings may be null, although getValue has @NonNull return
    public void loadInfrastructure(Configuration configuration) throws SetUpException {
        // Deny multiple calls of this method
        if (infrastructureLoaded) {
            throw new SetUpException("ComAnI already loaded");
        }
        // If not loaded, do that now
        String comaniPropertiesFilePath = configuration.getValue(DefaultSettings.COMANI_PROPERTIES_FILE_PATH);
        if (comaniPropertiesFilePath != null && !comaniPropertiesFilePath.isBlank()) {            
            LOGGER.logInfo("Loading ComAnI with configuration file " + comaniPropertiesFilePath);
            /*
             * The ComAnI setup was designed to accept the command line arguments as input only. In order to avoid
             * additional implementation effort, it is the easiest way to simply use a new array for that purpose.
             */
            String[] comaniArgs = {comaniPropertiesFilePath};
            try {
                // Create the internal ComAnI setup and related infrastructure properties
                /*
                 * TODO do not use the ComAnI setup as it has too restrictive checks, requires mandatory properties
                 * and creates an output directory, which are not used in this integration.
                 * Create the subset of necessary properties based on the properties file in separate method as part of
                 * loading the infrastructure.
                 * 
                 * It could also be an option to use optional KernelHaven settings instead of a separate ComAnI
                 * properties file. 
                 */
                Setup comaniSetup = Setup.getInstance(comaniArgs);
                Properties comaniCoreProperties = comaniSetup.getCoreProperties();
                
                // Prepare the ComAnI infrastructure utilities for later use to instantiate the ComAnI plug-ins
                String pluginsDirectoryPath = comaniCoreProperties.getProperty(Setup.PROPERTY_CORE_PLUGINS_DIR);
                File pluginsDirectory = new File(pluginsDirectoryPath); // Existence-check already done in ComAnI setup
                COMANI_UTILITIES.setPluginsDirectory(pluginsDirectory);
                
                // Instantiate and configure the core elements of ComAnI
                commitQueue = new CommitQueue(1);
                commitQueue.setState(QueueState.OPEN); // TODO Close the queue again? (destroyed at server stop anyway)
                commitExtractor = getCommitExtractor(comaniSetup, commitQueue);
                if (commitExtractor == null) {
                    throw new SetUpException("Instiatating ComAnI extractor failed");
                }
                commitAnalyzer = getCommitAnalyzer(comaniSetup, commitQueue);
                if (commitAnalyzer == null) {
                    throw new SetUpException("Instiatating ComAnI analyzer failed");
                }
                
                infrastructureLoaded = true;
                LOGGER.logInfo("Loaded ComAnI successfully");
            } catch (SetupException e) {
                throw new SetUpException("Loading ComAnI failed", e);
            }
        } else {
            throw new SetUpException(DefaultSettings.COMANI_PROPERTIES_FILE_PATH.getKey() + " is not specified; it must"
                    + "hold the absolute path to an existing ComAnI properties file");
        }
    }
    
    /**
     * Instantiates, configures, and returns the desired ComAnI commit extractor specified by
     * {@link Setup#PROPERTY_EXTRACTION_CLASS}.
     * 
     * @param comaniSetup the ComAnI setup representing the user-defined infrastructure configuration
     * @param commitQueue the commit queue the commit extractor requires as target to push its extracted commit
     *        information to
     * @return the desired ComAnI commit extractor or <code>null</code>, if instantiating that extractor fails  
     */
    private AbstractCommitExtractor getCommitExtractor(Setup comaniSetup, CommitQueue commitQueue) {
        Properties comaniExtractionProperties = comaniSetup.getExtractionProperties();
        String extractorClassName = comaniExtractionProperties.getProperty(Setup.PROPERTY_EXTRACTION_CLASS);
        LOGGER.logInfo("Instantiating ComAnI extractor " + extractorClassName);
        return COMANI_UTILITIES.instantiateExtractor(extractorClassName, comaniExtractionProperties, commitQueue);
    }
    
    /**
     * Instantiates, configures, and returns the desired ComAnI commit analyzer specified by
     * {@link Setup#PROPERTY_ANALYSIS_CLASS}.
     * 
     * @param comaniSetup the ComAnI setup representing the user-defined infrastructure configuration
     * @param commitQueue the commit queue The commit analyzer requires as source to receive commit information to
     *        analyze.
     * @return the desired ComAnI commit analyzer or <code>null</code>, if instantiating that analyzer fails  
     */
    private AbstractCommitAnalyzer getCommitAnalyzer(Setup comaniSetup, CommitQueue commitQueue) {
        Properties comaniAnalysisProperties = comaniSetup.getAnalysisProperties();
        String analyzerClassName = comaniAnalysisProperties.getProperty(Setup.PROPERTY_ANALYSIS_CLASS);
        LOGGER.logInfo("Instantiating ComAnI analyzer " + analyzerClassName);
        return COMANI_UTILITIES.instantiateAnalyzer(analyzerClassName, comaniAnalysisProperties, commitQueue);
    }
    
    /**
     * Analyzes the given commit.
     * TODO write proper JavaDoc
     * 
     * @param commitString the commit TODO
     * @return the return TODO
     * @throws IncrementalException the exception TOOD
     */
    public AnalysisResult analyze(String commitString) throws IncrementalException {
        AnalysisResult analysisResult = null;
        if (infrastructureLoaded) {
            if (commitExtractor.extract(commitString)) {
                // ComAnI extractors automatically add their extracted commits to the commit queue
                Commit commit = commitQueue.getCommit();
                if (commit != null) {                    
                    commitAnalyzer.analyze(commit);
                }
            } else {
                throw new IncrementalException("Extracting the current commit for commit analysis failed");
            }
        } else {
            throw new IncrementalException("ComAnI not loaded yet due to missing or non-successful call for loading"
                    + " that infrastructure");
        }
        return analysisResult;
    }
    
    /**
     * Returns the singleton instance of this class.<br>
     * <br>
     * Note that this instance requires calling {@link #loadInfrastructure(Configuration)} once and successfully before 
     * {@link #analyze(String)} is possible.
     * 
     * @return the singleton instance of this class; never <code>null</code>
     */
    public static ComAnI instance() {
        return instance;
    }
    
}
