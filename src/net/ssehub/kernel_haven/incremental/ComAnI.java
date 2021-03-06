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
import java.util.List;
import java.util.Properties;

import net.ssehub.comani.analysis.AbstractAnalysisResult;
import net.ssehub.comani.analysis.AbstractCommitAnalyzer;
import net.ssehub.comani.analysis.VerificationRelevantResult;
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
     * The result of analyzing a single given commit (string).
     */
    private VerificationRelevantResult commitAnalysisResult;

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
     *         ComAnI setup, or loading the ComAnI plug-ins fails
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
            IncrementalSetup incrementalSetup = IncrementalSetup.init(comaniPropertiesFilePath);
            
            // Prepare the ComAnI infrastructure utilities for later use to instantiate the ComAnI plug-ins
            Properties comaniCoreProperties = incrementalSetup.getCoreProperties();
            String pluginsDirectoryPath = comaniCoreProperties.getProperty(IncrementalSetup.PROPERTY_CORE_PLUGINS_DIR);
            File pluginsDirectory = new File(pluginsDirectoryPath); // Existence-check already done in IncrementalSetup
            COMANI_UTILITIES.setPluginsDirectory(pluginsDirectory);
            
            // Instantiate and configure the core elements of ComAnI
            commitQueue = new CommitQueue(1);
            commitQueue.setState(QueueState.OPEN); // TODO Close the queue again? (destroyed at server stop anyway)
            commitExtractor = getCommitExtractor(incrementalSetup, commitQueue);
            if (commitExtractor == null) {
                throw new SetUpException("Instantiating ComAnI extractor failed");
            }
            commitAnalyzer = getCommitAnalyzer(incrementalSetup, commitQueue);
            if (commitAnalyzer == null) {
                throw new SetUpException("Instantiating ComAnI analyzer failed");
            }
            
            infrastructureLoaded = true;
            LOGGER.logInfo("Loaded ComAnI successfully");
        } else {
            throw new SetUpException(DefaultSettings.COMANI_PROPERTIES_FILE_PATH.getKey() + " is not specified; it must"
                    + "hold the absolute path to an existing ComAnI properties file");
        }
    }
    
    /**
     * Instantiates, configures, and returns the desired ComAnI commit extractor specified by
     * {@link IncrementalSetup#PROPERTY_EXTRACTION_CLASS}.
     * 
     * @param incrementalSetup the ComAnI setup representing the user-defined infrastructure configuration
     * @param commitQueue the commit queue the commit extractor requires as target to push its extracted commit
     *        information to
     * @return the desired ComAnI commit extractor or <code>null</code>, if instantiating that extractor fails  
     */
    private AbstractCommitExtractor getCommitExtractor(IncrementalSetup incrementalSetup, CommitQueue commitQueue) {
        Properties comaniExtractionProperties = incrementalSetup.getExtractionProperties();
        String extractorClassName = comaniExtractionProperties.getProperty(IncrementalSetup.PROPERTY_EXTRACTION_CLASS);
        LOGGER.logInfo("Instantiating ComAnI extractor " + extractorClassName);
        return COMANI_UTILITIES.instantiateExtractor(extractorClassName, comaniExtractionProperties, commitQueue);
    }
    
    /**
     * Instantiates, configures, and returns the desired ComAnI commit analyzer specified by
     * {@link IncrementalSetup#PROPERTY_ANALYSIS_CLASS}.
     * 
     * @param incrementalSetup the ComAnI setup representing the user-defined infrastructure configuration
     * @param commitQueue the commit queue The commit analyzer requires as source to receive commit information to
     *        analyze.
     * @return the desired ComAnI commit analyzer or <code>null</code>, if instantiating that analyzer fails  
     */
    private AbstractCommitAnalyzer getCommitAnalyzer(IncrementalSetup incrementalSetup, CommitQueue commitQueue) {
        Properties comaniAnalysisProperties = incrementalSetup.getAnalysisProperties();
        String analyzerClassName = comaniAnalysisProperties.getProperty(IncrementalSetup.PROPERTY_ANALYSIS_CLASS);
        LOGGER.logInfo("Instantiating ComAnI analyzer " + analyzerClassName);
        return COMANI_UTILITIES.instantiateAnalyzer(analyzerClassName, comaniAnalysisProperties, commitQueue);
    }
    
    /**
     * Analyzes the given commit (string) using the ComAnI commit extractor and commit analyzer. The results of a
     * successful analysis can be obtained via {@link #getChangedCodeArtifacts()}, {@link #hasBuildArtifactChanges()},
     * and {@link #hasVariabilityModelArtifactChanges()}.
     * 
     * @param commitString the string containing a complete commit information
     * @throws IncrementalException if extracting or analyzing the given commit (string) fails or, if this method is
     *         called before {@link #loadInfrastructure(Configuration)}
     */
    public void analyze(String commitString) throws IncrementalException {
        LOGGER.logDebug("ComAnI starts analyzing the following commit:", commitString);
        // Reset the analysis result to avoid a previous result to be available even if the current analysis fails
        commitAnalysisResult = null;
        if (infrastructureLoaded) {
            if (commitExtractor.extract(commitString)) {
                // ComAnI extractors automatically add their extracted commits to the commit queue
                Commit commit = commitQueue.getCommit();
                if (commit != null) {                    
                    AbstractAnalysisResult analysisResult = commitAnalyzer.analyze(commit);
                    if (analysisResult != null) {
                        try {                            
                            this.commitAnalysisResult = (VerificationRelevantResult) analysisResult;
                            System.out.println("ComAnI Analysis Result:");
                            System.out.println(this.commitAnalysisResult);
                        } catch (ClassCastException e) {
                            throw new IncrementalException("Commit analysis provides unexpected result type", e);
                        }
                    } else {
                        throw new IncrementalException("Analyzing the current commit failed");
                    }
                } else {
                    throw new IncrementalException("Commit extraction successful, but no commit for commit analysis "
                            + "available");
                }
            } else {
                throw new IncrementalException("Extracting the current commit for commit analysis failed");
            }
        } else {
            throw new IncrementalException("ComAnI not loaded yet due to missing or non-successful call for loading"
                    + " that infrastructure");
        }
    }
    
    /**
     * Returns the identifier (e.g., SHA) of the latest analyzed commit.
     *  
     * @return the identifier (e.g., SHA) of the latest analyzed commit or <code>null</code>, if no commit analysis
     *         result or no commit identifier is available
     * @see #analyze(String)
     */
    public String getAnalyzedCommitIdentifier() {
        String commitIdentifier = null;
        if (commitAnalysisResult != null) {
            commitIdentifier = commitAnalysisResult.getCommitIdentifier();
        }
        return commitIdentifier;
    }
    
    /**
     * Returns the result of analyzing a given commit (string) in terms of the list of code artifacts (their relative
     * paths) for which the analyzed commit changes variability information. These paths are relative to the main
     * directory of the software (repository) under analysis, e.g. specified by {@link DefaultSettings#SOURCE_TREE}.
     * 
     * @return the list of relative paths to code artifact with changed variability information or <code>null</code>, if
     *         no commit analysis result is available; an empty list indicates no variability information changes in
     *         code artifacts
     * @see #analyze(String) 
     */
    public List<String> getChangedCodeArtifacts() {
        List<String> relevantCodeChanges = null;
        if (commitAnalysisResult != null) {
            relevantCodeChanges = commitAnalysisResult.getRelevantCodeChanges();
        }
        return relevantCodeChanges;
    }
    
    /**
     * Returns the result of analyzing a given commit (string) in terms of whether that commit changes variability
     * information in at least one build artifact.
     * 
     * @return <code>true</code>, if variability information in at least one build artifact changed; <code>false</code>
     *         otherwise, which indicates either no variability information changes in build artifacts or even no commit
     *         analysis result available
     * @see #analyze(String) 
     */
    public boolean hasBuildArtifactChanges() {
        boolean relevantBuildChanges = false;
        if (commitAnalysisResult != null) {
            relevantBuildChanges = commitAnalysisResult.getRelevantBuildChanges();
        }
        return relevantBuildChanges;
    }
    
    /**
     * Returns the result of analyzing a given commit (string) in terms of whether that commit changes variability
     * information in at least one variability model artifact.
     * 
     * @return <code>true</code>, if variability information in at least one variability model artifact changed;
     *         <code>false</code> otherwise, which indicates either no variability information changes in variability
     *         model artifacts or even no commit analysis result available
     * @see #analyze(String) 
     */
    public boolean hasVariabilityModelArtifactChanges() {
        boolean relevantVariabilityModelChanges = false;
        if (commitAnalysisResult != null) {
            relevantVariabilityModelChanges = commitAnalysisResult.getRelevantVariabilityModelChanges();
        }
        return relevantVariabilityModelChanges;
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
