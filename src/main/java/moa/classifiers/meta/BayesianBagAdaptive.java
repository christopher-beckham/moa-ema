package moa.classifiers.meta;

import java.util.Arrays;

import org.apache.commons.math3.distribution.GammaDistribution;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Utils;
import moa.core.DoubleVector;
import moa.core.Measurement;
import moa.core.MiscUtils;
import moa.options.ClassOption;
import moa.options.IntOption;

public class BayesianBagAdaptive extends AbstractClassifier {

    @Override
    public String getPurposeString() {
        return "Adaptive Bayesian bagging";
    }
        
    private static final long serialVersionUID = 1L;

    public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'l',
            "Classifier to train.", Classifier.class, "trees.HoeffdingTree");

    public IntOption ensembleSizeOption = new IntOption("ensembleSize", 's',
            "The number of models in the bag.", 10, 1, Integer.MAX_VALUE);
    
    protected Classifier[] ensemble;
    
    protected transient GammaDistribution m_gammaDefault = null;
    
    private double[] m_classFreqs = null;
    private double m_instCounts = 0;
    
    private boolean m_debug = false;
    
    public void setDebug(boolean b) {
    	m_debug = b;
    }
    
    public double[] getClassFreqs() {
    	return m_classFreqs;
    }
    
    public double getInstCounts() {
    	return m_instCounts;
    }

    @Override
    public void resetLearningImpl() {
        this.ensemble = new Classifier[this.ensembleSizeOption.getValue()];
        Classifier baseLearner = (Classifier) getPreparedClassOption(this.baseLearnerOption);
        baseLearner.resetLearning();
        for (int i = 0; i < this.ensemble.length; i++) {
            this.ensemble[i] = baseLearner.copy();
        }
        m_classFreqs = null;
        m_instCounts = 1; // laplace
        m_gammaDefault = new GammaDistribution(1,1);
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {
    	if(m_classFreqs == null) {
    		m_classFreqs = new double[ inst.numClasses() ];
    		for(int x = 0; x < m_classFreqs.length; x++) {
    			m_classFreqs[x] = 1; // laplace
    		}
    	}
    	
    	int classValue = (int)inst.classValue();
    	
    	if(m_instCounts < 0) {
	    	for(int i = 0; i < this.ensemble.length; i++) {
	    		//weights[i] = MiscUtils.poisson(1, this.classifierRandom);
	    		Instance weightedInst = (Instance) inst.copy();
	    		weightedInst.setWeight( m_gammaDefault.sample() );
	    		this.ensemble[i].trainOnInstance(weightedInst);
	    	} 	
    	} else {
    		// old formulation:
    		// w ~ gamma( (xj + 1) / c , N+1 )
    		/*
    		double c = 0;
    		for(int x = 0; x < m_classFreqs.length; x++) {
    			if( x == classValue) {
    				c += (m_classFreqs[x]+1)*(m_classFreqs[x]+1);
    			} else {
    				c += (m_classFreqs[x]*m_classFreqs[x]);
    			}
    		}
    		c = c / ((m_instCounts+1)*(m_instCounts+1));
    		*/
    		
    		double k = 1;
    		
    		// for each model in the ensemble...
    		for (int i = 0; i < this.ensemble.length; i++) {
    			
    			//GammaDistribution g = new GammaDistribution( (m_classFreqs[classValue]+1)/c, 1/(m_instCounts+1));
    			GammaDistribution g = new GammaDistribution( (k*m_classFreqs[classValue])/m_instCounts,
    					m_instCounts/(k*m_classFreqs[classValue]) );	
    			
    			g.reseedRandomGenerator( classifierRandom.nextLong() );
    			double weight = g.sample();
    			
    			if(m_debug) {
    			//	System.out.println("freqs = " + Arrays.toString(m_classFreqs) + ", c = " + c + ", params = (" + (m_classFreqs[classValue]/c) + ","
    			//			+ (m_instCounts+1) + "), weight: " + weight);
    				System.out.println("**debug**");
    				System.out.println("  freqs = " + Arrays.toString(m_classFreqs) );
    				System.out.println("  freqs for this class = " + m_classFreqs[classValue]);
    				System.out.println("  tot = " + m_instCounts);
    				
    				System.out.println("  k = " + ((k*m_classFreqs[classValue]) / m_instCounts) );
    				System.out.println("  theta = " + (m_instCounts) / (k*m_classFreqs[classValue]) );
    				
    				System.out.println("  E[W] = " + (m_classFreqs[classValue]/m_instCounts) * (m_instCounts/m_classFreqs[classValue]) );
    				System.out.println("  Var[W] = " + m_instCounts/(k*m_classFreqs[classValue]) );
    				
    			}   			
	    		
	        	Instance weightedInst = (Instance) inst.copy();
	        	//weightedInst.setWeight( weightsForAllClasses[classValue] );
	        	weightedInst.setWeight(weight);
	        	this.ensemble[i].trainOnInstance(weightedInst);
    		}
    		
    	}
    	m_instCounts += 1;
    	m_classFreqs[classValue] += 1;
    }

    @Override
    public double[] getVotesForInstance(Instance inst) {
        DoubleVector combinedVote = new DoubleVector();
        for (int i = 0; i < this.ensemble.length; i++) {
            DoubleVector vote = new DoubleVector(this.ensemble[i].getVotesForInstance(inst));
            if (vote.sumOfValues() > 0.0) {
                vote.normalize();
                combinedVote.addValues(vote);
            }
        }
        return combinedVote.getArrayRef();
    }

    @Override
    public boolean isRandomizable() {
        return true;
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {
        // TODO Auto-generated method stub
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return new Measurement[]{new Measurement("ensemble size",
                    this.ensemble != null ? this.ensemble.length : 0)};
    }

    @Override
    public Classifier[] getSubClassifiers() {
        return this.ensemble.clone();
    }
}