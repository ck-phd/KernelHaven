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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

import net.ssehub.comani.core.Logger;
import net.ssehub.kernel_haven.SetUpException;

/**
 * This class reads the ComAnI properties file and provides the user-defined properties in that file for other ComAnI
 * components.
 * 
 * @author Christian Kroeher
 *
 */
public class IncrementalSetup {
    
    /**
     * The string representation of the property's key identifying the operating system ComAnI is currently running on.
     * This is automatically detected by this class.
     */
    public static final String PROPERTY_CORE_OS = "core.os";
    
    /**
     * The string representation of the property's key identifying the user-defined path to the directory, in which the
     * ComAnI extractor and analyzer plug-ins are located.
     */
    public static final String PROPERTY_CORE_PLUGINS_DIR = "core.plugins_dir";
    
    /**
     * The string representation of the property's key identifying the user-defined name of target version control
     * system, e.g., from which the commits will be extracted.
     */
    public static final String PROPERTY_CORE_VERSION_CONTROL_SYSTEM = "core.version_control_system";
    
    /**
     * The string representation of the property's key identifying the user-defined log level. This is an optional
     * property, hence, if not specified, the default log-level "0" (silent" will be used.
     */
    public static final String PROPERTY_CORE_LOG_LEVEL = "core.log_level";
    
    /**
     * The string representation of the property's key identifying the user-defined ComAnI extractor main class name.
     */
    public static final String PROPERTY_EXTRACTION_CLASS = "extraction.extractor";
    
    /**
     * The string representation of the property's key identifying the user-defined ComAnI analyzer main class name.
     */
    public static final String PROPERTY_ANALYSIS_CLASS = "analysis.analyzer";
    
    /**
     * The logger for logging messages.
     */
    private static final net.ssehub.kernel_haven.util.Logger LOGGER = net.ssehub.kernel_haven.util.Logger.get();
    
    /**
     * The singleton instance of this class.
     */
    private static IncrementalSetup instance;
    
    /**
     * The core properties for setting up ComAnI, e.g., the operating system, or the path to the directory, in which the
     * extractor and analyzer plug-ins are located. In general, this properties object contains all properties defined
     * in the properties file, which start with the prefix "<i>core.</i>".
     * 
     */
    private Properties coreProperties;
    
    /**
     * The properties determining the extraction process, e.g., the fully-qualified class name of the extractor to use.
     * In general, this properties object contains all properties defined in the properties file, which start with the
     * prefix "<i>extraction.</i>".<br>
     * <br> 
     * <b>Note</b> that these properties are exclusive to the extraction process and the extractor in use. For example,
     * an analysis may not be able to access these properties.
     */
    private Properties extractionProperties;
    
    /**
     * The properties determining the analysis process, e.g., the fully-qualified class name of the analyzer to use.
     * In general, this properties object contains all properties defined in the properties file, which start with the
     * prefix "<i>analysis.</i>".<br>
     * <br> 
     * <b>Note</b> that these properties are exclusive to the analysis process and the analyzer in use. For example,
     * an extractor may not be able to access these properties.
     */
    private Properties analysisProperties;
    
    /**
     * Constructs the singleton instance of this class.
     * 
     * @param comaniPropertiesFilePath the file containing the property specification to configure ComAnI; must not be
     *        <code>null</code>
     * @throws SetUpException if creating the setup fails due to missing or false properties
     */
    private IncrementalSetup(String comaniPropertiesFilePath) throws SetUpException {
        createProperties(comaniPropertiesFilePath);
    }
    
    /**
     * Creates the internal {@link #coreProperties}, {@link #extractionProperties}, and {@link #analysisProperties}
     * based on the property specification in the file denoted by the given file path.
     * 
     * @param comaniPropertiesFilePath the file containing the property specification to configure ComAnI; must not be
     *        <code>null</code>
     * @throws SetUpException if mandatory properties are missing or have invalid values
     */
    private void createProperties(String comaniPropertiesFilePath) throws SetUpException {
        File propertiesFile = new File(comaniPropertiesFilePath);
        if (propertiesFile.exists()) {
            if (propertiesFile.isFile()) {
                InputStream inStream = null;
                try {
                    // Read all properties
                    inStream = new FileInputStream(propertiesFile);
                    Properties loadedProperties = new Properties();
                    loadedProperties.load(inStream);
                    // Categorize properties
                    Enumeration<Object> propertyKeys = loadedProperties.keys();
                    coreProperties = new Properties();
                    extractionProperties = new Properties();
                    analysisProperties = new Properties();
                    while (propertyKeys.hasMoreElements()) {
                        String key = (String) propertyKeys.nextElement();
                        String value = loadedProperties.getProperty(key);
                        if (key.startsWith("core.")) {
                            coreProperties.put(key, value.trim());
                        } else if (key.startsWith("extraction.")) {
                            extractionProperties.put(key, value.trim());
                        } else if (key.startsWith("analysis.")) {
                            analysisProperties.put(key, value.trim());
                        }
                    }
                    coreProperties.put(PROPERTY_CORE_OS, System.getProperty("os.name"));
                    // Include OS and version control system in extraction properties for support checks
                    extractionProperties.put(PROPERTY_CORE_OS, System.getProperty("os.name"));
                    extractionProperties.put(PROPERTY_CORE_VERSION_CONTROL_SYSTEM,
                            coreProperties.get(PROPERTY_CORE_VERSION_CONTROL_SYSTEM));
                    // Include OS and version control system in analysis properties for support checks
                    analysisProperties.put(PROPERTY_CORE_OS, System.getProperty("os.name"));
                    analysisProperties.put(PROPERTY_CORE_VERSION_CONTROL_SYSTEM,
                            coreProperties.get(PROPERTY_CORE_VERSION_CONTROL_SYSTEM));
                } catch (IOException e) {
                    throw new SetUpException("Reading ComAnI properties file " + comaniPropertiesFilePath + " failed",
                            e);
                } finally {
                    if (inStream != null) {
                        try {
                            inStream.close();
                        } catch (IOException e) {
                            throw new SetUpException("Closing input stream for reading ComAnI properties file "
                                    + comaniPropertiesFilePath + " failed", e);
                        }
                    }
                }
            } else {
                throw new SetUpException("ComAnI properties file " + comaniPropertiesFilePath + " is not a file");
            }
        } else {
            throw new SetUpException("ComAnI properties file " + comaniPropertiesFilePath + " does not exist");
        }
        // Check properties for correctness
        checkProperties();
    }
    
    /**
     * Checks whether the created properties, their values, and their combination are valid.
     * 
     * @throws SetUpException if the created properties, their values, and their combination are not valid
     */
    private void checkProperties() throws SetUpException {
        checkCoreProperties();
        checkExtractorProperty();
        checkAnalysisProperties();
    }
    
    /**
     * Checks whether the {@link #coreProperties}, their values, and their combination are valid.
     * 
     * @throws SetUpException if core properties, their values, or their combination are not valid
     */
    private void checkCoreProperties() throws SetUpException {
        // Check if operating system is available
        if (coreProperties.get(PROPERTY_CORE_OS) == null) {
            throw new SetUpException("ComAnI property " + PROPERTY_CORE_OS + " not specified");
        }
        // Check if plug-ins directory is valid
        checkCorePluginsDirectory();
        // Check if log-level is valid; if not, use default level
        String logLevelString = coreProperties.getProperty(PROPERTY_CORE_LOG_LEVEL);
        if (logLevelString != null) {
            try {
                int logLevel = Integer.parseInt(logLevelString);
                if (logLevel < 0 || logLevel > 2) {
                    // No exception needed, use "silent" log-level
                    Logger.getInstance().setLogLevel("0");
                    coreProperties.put(PROPERTY_CORE_LOG_LEVEL, 0);
                    LOGGER.logInfo("ComAnI log level set to 0 (silent)");
                } else {
                    // Set the (valid) user-defined log-level
                    Logger.getInstance().setLogLevel(logLevelString);
                    LOGGER.logInfo("ComAnI log level set to " + logLevelString);
                }
            } catch (NumberFormatException e) {
                throw new SetUpException("ComAnI property " + PROPERTY_CORE_LOG_LEVEL + " value " + logLevelString
                        + " is not a number");
            }
        } else {
            // No exception needed, use "silent" log-level
            Logger.getInstance().setLogLevel("0");
            coreProperties.put(PROPERTY_CORE_LOG_LEVEL, 0);
            LOGGER.logInfo("ComAnI log level set to 0 (silent)");
        }
    }
    
    /**
     * Checks if the ComAnI plug-ins directory is valid and contains Jar-files.
     * 
     * @throws SetUpException if check fails
     */
    private void checkCorePluginsDirectory() throws SetUpException {
        String pluginsDirectoryPath = coreProperties.getProperty(PROPERTY_CORE_PLUGINS_DIR);
        if (pluginsDirectoryPath != null && !pluginsDirectoryPath.isEmpty()) {
            File pluginsDirectory = new File(pluginsDirectoryPath);
            if (pluginsDirectory.exists()) {
                if (pluginsDirectory.isDirectory()) {
                    File[] jarFiles = pluginsDirectory.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            boolean acceptFile = false;
                            if (name.toLowerCase().endsWith(".jar")) {
                                acceptFile = true;
                            }
                            return acceptFile;
                        }
                    });
                    if (jarFiles == null) {
                        throw new SetUpException("I/O error occured while checking the ComAnI plug-ins directory " 
                                + pluginsDirectory.getAbsolutePath());
                    } else if (jarFiles.length == 0) {
                        throw new SetUpException("ComAnI plug-ins directory " + pluginsDirectory.getAbsolutePath()
                                + " does not contain any Jar-files");
                    }
                } else {
                    throw new SetUpException("ComAnI plug-ins directory " + pluginsDirectory.getAbsolutePath()
                            + " is not a directory");
                }
            } else {
                throw new SetUpException("ComAnI plug-ins directory " + pluginsDirectory.getAbsolutePath()
                        + " does not exist");
            }
        } else {
            throw new SetUpException("ComAnI property " + PROPERTY_CORE_PLUGINS_DIR + " not specified");
        }
    }
    
    /**
     * Checks if the fully-qualified main class name of the commit extractor is specified.
     * 
     * @throws SetUpException if that name is not specified
     */
    private void checkExtractorProperty() throws SetUpException {
        if (extractionProperties.getProperty(PROPERTY_EXTRACTION_CLASS) == null) {
            throw new SetUpException("ComAnI property " + PROPERTY_EXTRACTION_CLASS + " not specified");
        }
    }
    
    /**
     * Checks if the fully-qualified main class name of the commit analyzer is specified.
     * 
     * @throws SetUpException if that name is not specified
     */
    private void checkAnalysisProperties() throws SetUpException {
        if (analysisProperties.getProperty(PROPERTY_ANALYSIS_CLASS) == null) {
            throw new SetUpException("ComAnI property " + PROPERTY_ANALYSIS_CLASS + " not specified");
        }
    }
    
    /**
     * Returns the {@link #coreProperties} as defined in the ComAnI properties file.
     * 
     * @return the core properties for setting up ComAnI; never <code>null</code> but may be empty
     */
    public Properties getCoreProperties() {
        return coreProperties;
    }
    
    /**
     * Returns the {@link #extractionProperties} as defined in the ComAnI properties file.
     * 
     * @return the extraction properties for setting up ComAnI; never <code>null</code> but may be empty
     */
    public Properties getExtractionProperties() {
        return extractionProperties;
    }
    
    /**
     * Returns the {@link #analysisProperties} as defined in the ComAnI properties file.
     * 
     * @return the analysis properties for setting up ComAnI; never <code>null</code> but may be empty
     */
    public Properties getAnalysisProperties() {
        return analysisProperties;
    }
    
    /**
     * Creates the incremental setup based on properties file denoted by the given properties file path.
     * 
     * @param comaniPropertiesFilePath the file containing the property specification to configure ComAnI; must not be
     *        <code>null</code>
     * @return the incremental setup representing the desired ComAnI configuration
     * @throws SetUpException if a setup already exists or creating the setup fails due to missing or false properties
     */
    public static IncrementalSetup init(String comaniPropertiesFilePath) throws SetUpException {
        if (instance != null) {
            throw new SetUpException("ComAnI setup already initialized: new properties file " 
                    + comaniPropertiesFilePath + " rejected");
        }
        instance = new IncrementalSetup(comaniPropertiesFilePath);
        return instance;
    }
    
}
