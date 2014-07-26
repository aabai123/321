/*
 * ARX: Efficient, Stable and Optimal Data Anonymization
 * Copyright (C) 2012 - 2014 Florian Kohlmayer, Fabian Prasser
 * Copyright (C) 2014 Karol Babioch <karol@babioch.de>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.deidentifier.arx.gui.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deidentifier.arx.ARXAnonymizer;
import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.ARXLattice.ARXNode;
import org.deidentifier.arx.ARXResult;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.DataDefinition;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataSubset;
import org.deidentifier.arx.criteria.DPresence;
import org.deidentifier.arx.criteria.Inclusion;
import org.deidentifier.arx.criteria.PrivacyCriterion;

public class Model implements Serializable {

	private static final long serialVersionUID = -7669920657919151279L;

    private transient ARXAnonymizer               anonymizer                      = null;
    private transient Set<ARXNode>                clipboard                       = new HashSet<ARXNode>();
    private boolean                               debugEnabled                    = false;
    private String                                description;
    private ModelDPresenceCriterion               dPresenceModel                  = new ModelDPresenceCriterion();
    private int[]                                 groups;
    private int                                   historySize                     = 200;

    private int                                   initialNodesInViewer            = 100;
    private long                                  inputBytes                      = 0L;
    private ModelConfiguration                    inputConfig                     = new ModelConfiguration();
    private ModelKAnonymityCriterion              kAnonymityModel                 = new ModelKAnonymityCriterion();
    private Map<String, ModelLDiversityCriterion> lDiversityModel                 = new HashMap<String, ModelLDiversityCriterion>();
    private int                                   maximalSizeForComplexOperations = 5000000;
    private int                                   maxNodesInLattice               = 100000;
    private int                                   maxNodesInViewer                = 700;
    private boolean                               modified                        = false;

    private String                                name                            = null;
    private ModelNodeFilter                       nodeFilter                      = null;
    private String                                optimalNodeAsString;
    private transient DataHandle                  output                          = null;
    private ModelConfiguration                    outputConfig                    = null;

    private transient ARXNode                     outputNode                      = null;
    private String                                outputNodeAsString;

    private String[]                              pair                            = new String[] { null, null };

    private transient String                      path                            = null;
    private String                                query                           = "";                                             //$NON-NLS-1$

    private transient ARXResult                   result                          = null;

    private String                                selectedAttribute               = null;

    private transient ARXNode                     selectedNode                    = null;
    private char                                  separator                       = ';';                                            //$NON-NLS-1$
    private Boolean                               showVisualization               = true;
    private double                                snapshotSizeDataset             = 0.2d;

    private double                                snapshotSizeSnapshot            = 0.8d;
    private String                                subsetOrigin                    = "All";                                          //$NON-NLS-1$
    private String                                suppressionString               = "*";                                            //$NON-NLS-1$

    private Map<String, ModelTClosenessCriterion> tClosenessModel                 = new HashMap<String, ModelTClosenessCriterion>();
    private long                                  time;

    private ModelViewConfig                       viewConfig                      = new ModelViewConfig();


    public Model(final String name, final String description) {
		this.name = name;
		this.description = description;
		setModified();
	}

	public ARXAnonymizer createAnonymizer() {
	    
		// Initialize anonymizer
		this.anonymizer = new ARXAnonymizer();
		this.anonymizer.setHistorySize(getHistorySize());
		this.anonymizer.setMaximumSnapshotSizeDataset(getSnapshotSizeDataset());
		this.anonymizer.setSuppressionString(getSuppressionString());
		this.anonymizer.setMaximumSnapshotSizeSnapshot(getSnapshotSizeSnapshot());
		this.anonymizer.setRemoveOutliers(inputConfig.isRemoveOutliers());
		
		// Add all criteria
		this.createConfig();

        // Return the anonymizer
		return anonymizer;
	}
	
	public void createClonedConfig() {

        // Clone the config
        outputConfig = inputConfig.clone();
        this.setModified();
	}
	
	public void createConfig() {

		ModelConfiguration config = getInputConfig();
		DataDefinition definition = getInputDefinition();
		
		// Initialize the config
		config.removeAllCriteria();
		if (definition == null) return;

		// Initialize definition
        for (String attr : definition.getQuasiIdentifyingAttributes()) {
            
            Hierarchy hierarchy = config.getHierarchy(attr);
            /* Handle non-existent hierarchies*/
            if (hierarchy == null || hierarchy.getHierarchy()==null) {
                hierarchy = Hierarchy.create();
                config.setHierarchy(attr, hierarchy);
            }
            Integer min = config.getMinimumGeneralization(attr);
            Integer max = config.getMaximumGeneralization(attr);
            
            if (min==null){ min = 0; }
            if (max==null) {
                if (hierarchy.getHierarchy().length==0){ max = 0; } 
                else { max = hierarchy.getHierarchy()[0].length-1; }
            }
            definition.setAttributeType(attr, hierarchy);
            definition.setMinimumGeneralization(attr, min);
            definition.setMaximumGeneralization(attr, max);
        }
        
		if (this.kAnonymityModel != null &&
		    this.kAnonymityModel.isActive() &&
		    this.kAnonymityModel.isEnabled()) {
		    config.addCriterion(this.kAnonymityModel.getCriterion(this));
		}

        if (this.dPresenceModel != null && 
            this.dPresenceModel.isActive() && 
            this.dPresenceModel.isEnabled()) {
            config.addCriterion(this.dPresenceModel.getCriterion(this));
        }
		
		for (Entry<String, ModelLDiversityCriterion> entry : this.lDiversityModel.entrySet()){
	        if (entry.getValue() != null &&
	            entry.getValue().isActive() &&
	            entry.getValue().isEnabled()) {
	            config.addCriterion(entry.getValue().getCriterion(this));
	        }
		}
        
        for (Entry<String, ModelTClosenessCriterion> entry : this.tClosenessModel.entrySet()){
            if (entry.getValue() != null &&
                entry.getValue().isActive() &&
                entry.getValue().isEnabled()) {
                
                if (entry.getValue().getVariant()==1){ // EMD with hierarchy
                    if (config.getHierarchy(entry.getValue().getAttribute())==null){
                        config.setHierarchy(entry.getValue().getAttribute(), Hierarchy.create());
                    }
                }
                
                PrivacyCriterion criterion = entry.getValue().getCriterion(this);
                config.addCriterion(criterion);
            }
        }

        // Allow adding removing tuples
        if (!config.containsCriterion(DPresence.class)){
            if (getInputConfig() != null && getInputConfig().getInput() != null &&
                getInputConfig().getResearchSubset() != null) {
                DataSubset subset = DataSubset.create(config.getInput(), 
                                                      config.getResearchSubset());
                config.addCriterion(new Inclusion(subset));
            }
        }
	}
	
	public ARXConfiguration createSubsetConfig() {

		// Create a temporary config
		ARXConfiguration config = ARXConfiguration.create();

        // Add an enclosure criterion
        DataSubset subset = DataSubset.create(getInputConfig().getInput(), 
                                              getInputConfig().getResearchSubset());
		config.addCriterion(new Inclusion(subset));

        // Return the config
		return config;
	}

    public ARXAnonymizer getAnonymizer() {
		return anonymizer;
	}
    
	public String[] getAttributePair() {
		if (pair == null) pair = new String[] { null, null };
		return pair;
	}

	public Set<ARXNode> getClipboard() {
		return clipboard;
	}
    
	public String getDescription() {
		return description;
	}

	public ModelDPresenceCriterion getDPresenceModel() {
		return dPresenceModel;
	}

	public int[] getGroups() {
		// TODO: Refactor to colors[groups[row]]
		return this.groups;
	}
	
	/**
	 * @return the historySize
	 */
	public int getHistorySize() {
		return historySize;
	}

	public int getInitialNodesInViewer() {
		return initialNodesInViewer;
	}

	public long getInputBytes() {
		return inputBytes;
	}

	public ModelConfiguration getInputConfig() {
		return inputConfig;
	}

	public DataDefinition getInputDefinition(){
	    if (inputConfig==null) return null;
	    else if (inputConfig.getInput()==null) return null;
	    else return inputConfig.getInput().getDefinition();
	}

	public ModelKAnonymityCriterion getKAnonymityModel() {
		return kAnonymityModel;
	}

	public Map<String, ModelLDiversityCriterion> getLDiversityModel() {
		return lDiversityModel;
	}

	public int getMaximalSizeForComplexOperations(){
	    return this.maximalSizeForComplexOperations;
	}

	public int getMaxNodesInLattice() {
		return maxNodesInLattice;
	}

	public int getMaxNodesInViewer() {
		return maxNodesInViewer;
	}

	public String getName() {
		return name;
	}

	public ModelNodeFilter getNodeFilter() {
		return nodeFilter;
	}

	public String getOptimalNodeAsString() {
		return optimalNodeAsString;
	}

	/**
	 * @return the output
	 */
	public DataHandle getOutput() {
		return output;
	}

	public ModelConfiguration getOutputConfig() {
		return outputConfig;
	}

	public DataDefinition getOutputDefinition(){
		if (this.output == null) return null;
		else return this.output.getDefinition();
	}

	public ARXNode getOutputNode() {
		return outputNode;
	}

	public String getOutputNodeAsString() {
		return outputNodeAsString;
	}

	public String getPath() {
		return path;
	}

	public String getQuery() {
        return query;
    }

	/**
	 * @return the result
	 */
	public ARXResult getResult() {
		return result;
	}

	/**
	 * Returns the currently selected attribute
	 * 
	 * @return
	 */
	public String getSelectedAttribute() {
		return selectedAttribute;
	}

	public ARXNode getSelectedNode() {
		return selectedNode;
	}

	public char getSeparator() {
		return separator;
	}

	/**
	 * @return the snapshotSizeDataset
	 */
	public double getSnapshotSizeDataset() {
		return snapshotSizeDataset;
	}

	public double getSnapshotSizeSnapshot() {
		return snapshotSizeSnapshot;
	}

	public String getSubsetOrigin(){
        return this.subsetOrigin;
    }
	
	/**
	 * @return the suppressionString
	 */
	public String getSuppressionString() {
		return suppressionString;
	}
	
	public Map<String, ModelTClosenessCriterion> getTClosenessModel() {
		return tClosenessModel;
	}

	public long getTime() {
		return time;
	}

	public ModelViewConfig getViewConfig() {
        return this.viewConfig;
    }

	public boolean isDebugEnabled() {
	    return debugEnabled;
	}

	public boolean isModified() {
		if (inputConfig.isModified()) {
			return true;
		}
		if ((outputConfig != null) && outputConfig.isModified()) {
			return true;
		}
		return modified;
	}

	public boolean isQuasiIdentifierSelected() {
		return (getInputDefinition().getAttributeType(getSelectedAttribute()) instanceof Hierarchy);
	}

	public boolean isSensitiveAttributeSelected() {
		return (getInputDefinition().getAttributeType(getSelectedAttribute()) == AttributeType.SENSITIVE_ATTRIBUTE);
	}

    /**
     * Checks whether the lattice is too large
     * 
     * @return
     */

	public boolean isValidLatticeSize() {

		DataDefinition definition = getInputDefinition();
		int size = 1;
		for (final String attr : definition.getQuasiIdentifyingAttributes()) {
			final int factor = definition.getMaximumGeneralization(attr) -
					           definition.getMinimumGeneralization(attr);
			size *= factor;
		}
		return size <= maxNodesInLattice;
	}

	public boolean isVisualizationEnabled(){
	    if (this.showVisualization == null) {
	        return true;
	    } else {
	        return this.showVisualization;
	    }
	}

	public void reset() {
		// TODO: Need to reset more fields
		resetCriteria();
		inputConfig = new ModelConfiguration();
		outputConfig = null;
		output = null;
		result = null;
	}

	public void resetAttributePair() {
		if (pair == null)
			pair = new String[] { null, null };
		pair[0] = null;
		pair[1] = null;
	}

	public void resetCriteria() {
		
		if (inputConfig==null || inputConfig.getInput()==null) return;
		
		kAnonymityModel = new ModelKAnonymityCriterion();
		dPresenceModel = new ModelDPresenceCriterion();
		lDiversityModel.clear();
		tClosenessModel.clear();
		DataHandle handle = inputConfig.getInput().getHandle();
		for (int col = 0; col < handle.getNumColumns(); col++) {
			String attribute = handle.getAttributeName(col);
			lDiversityModel.put(attribute, new ModelLDiversityCriterion(
					attribute));
			tClosenessModel.put(attribute, new ModelTClosenessCriterion(
					attribute));
		}
	}

	public void setAnonymizer(final ARXAnonymizer anonymizer) {
		setModified();
		this.anonymizer = anonymizer;
	}

	public void setClipboard(final HashSet<ARXNode> set) {
		setModified();
		clipboard = set;
	}

	public void setDebugEnabled(boolean value){
	    this.debugEnabled = value;
	    this.setModified();
	}

	public void setDescription(final String description) {
		this.description = description;
		setModified();
	}

	public void setGroups(int[] groups) {
		this.groups = groups;
	}

	/**
	 * @param historySize
	 *            the historySize to set
	 */
	public void setHistorySize(final int historySize) {
		this.historySize = historySize;
		setModified();
	}

	public void setInitialNodesInViewer(final int val) {
		initialNodesInViewer = val;
		setModified();
	}

	public void setInputBytes(final long inputBytes) {
		setModified();
		this.inputBytes = inputBytes;
	}

	public void setInputConfig(final ModelConfiguration config) {
		inputConfig = config;
	}

	public void setMaximalSizeForComplexOperations(int numberOfRows) {
        this.maximalSizeForComplexOperations = numberOfRows;
        this.setModified();
    }

	public void setMaxNodesInLattice(final int maxNodesInLattice) {
		this.maxNodesInLattice = maxNodesInLattice;
		setModified();
	}

	public void setMaxNodesInViewer(final int maxNodesInViewer) {
		this.maxNodesInViewer = maxNodesInViewer;
		setModified();
	}

	public void setName(final String name) {
		this.name = name;
		setModified();
	}

	public void setNodeFilter(final ModelNodeFilter filter) {
		nodeFilter = filter;
		setModified();
	}

	/**
	 * @param output
	 *            the output to set
	 */
	public void setOutput(final DataHandle output, final ARXNode node) {
		this.output = output;
		this.outputNode = node;
		if (node != null) {
			outputNodeAsString = Arrays.toString(node.getTransformation());
		} else {
		    outputNodeAsString = null;
		}
		setModified();
	}

	public void setOutputConfig(final ModelConfiguration config) {
		outputConfig = config;
	}

	public void setPath(final String path) {
		this.path = path;
	}
	public void setQuery(String query){
        this.query = query;
        setModified();
    }
	
	/**
	 * @param result
	 *            the result to set
	 */
	public void setResult(final ARXResult result) {
		this.result = result;
		if ((result != null) && (result.getGlobalOptimum() != null)) {
			optimalNodeAsString = Arrays.toString(result.getGlobalOptimum()
					.getTransformation());
		}
		setModified();
	}
	
	public void setSaved() {
		modified = false;
	}

	public void setSelectedAttribute(final String attribute) {
		selectedAttribute = attribute;

		if (pair == null)
			pair = new String[] { null, null };
		if (pair[0] == null) {
			pair[0] = attribute;
			pair[1] = null;
		} else if (pair[1] == null) {
			pair[1] = attribute;
		} else {
			pair[0] = pair[1];
			pair[1] = attribute;
		}

		setModified();
	}

	public void setSelectedNode(final ARXNode node) {
		selectedNode = node;
		setModified();
	}

	public void setSeparator(final char separator) {
		this.separator = separator;
	}

	/**
	 * @param snapshotSizeDataset
	 *            the snapshotSizeDataset to set
	 */
	public void setSnapshotSizeDataset(final double snapshotSize) {
		snapshotSizeDataset = snapshotSize;
		setModified();
	}

	public void setSnapshotSizeSnapshot(final double snapshotSize) {
		setModified();
		snapshotSizeSnapshot = snapshotSize;
	}

    public void setSubsetManual(){
        if (!this.subsetOrigin.endsWith("manual")) {
            this.subsetOrigin += " + manual";
        }
    }
    
    public void setSubsetOrigin(String origin){
        this.subsetOrigin = origin;
    }
    
    /**
	 * @param suppressionString
	 *            the suppressionString to set
	 */
	public void setSuppressionString(final String suppressionString) {
		this.suppressionString = suppressionString;
		setModified();
	}
    
    public void setTime(final long time) {
		this.time = time;
	}
    
    public void setUnmodified() {
		modified = false;
		inputConfig.setUnmodified();
		if (outputConfig != null) {
			outputConfig.setUnmodified();
		}
	}

    public void setViewConfig(ModelViewConfig viewConfig) {
        this.viewConfig = viewConfig;
    }
    
    public void setVisualizationEnabled(boolean value){
        this.showVisualization = value;
        this.setModified();
    }

    private void setModified() {
		modified = true;
	}
}
