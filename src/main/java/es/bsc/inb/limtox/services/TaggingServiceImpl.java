package es.bsc.inb.limtox.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.MentionsAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import es.bsc.inb.limtox.model.LiverMarkerTerm;

@Service
public class TaggingServiceImpl implements TaggingService{
	
	static final Logger taggingLog = Logger.getLogger("taggingLog");
	
	Map<String, LiverMarkerTerm> hepatotoxicityTermsDict = new HashMap<String, LiverMarkerTerm>();
	
	public void execute(String propertiesParametersPath) {
		try {
			taggingLog.info("Tagging hepatotoxicity terms with properties :  " +  propertiesParametersPath);
			Properties propertiesParameters = this.loadPropertiesParameters(propertiesParametersPath);
			taggingLog.info("Input directory with the articles to tag : " + propertiesParameters.getProperty("inputDirectory"));
			taggingLog.info("Output directory : " + propertiesParameters.getProperty("outputDirectory"));
			taggingLog.info(" hepatotoxicity dictionary used : " + propertiesParameters.getProperty("hepatotoxicityJSONDict"));
			
			String inputDirectoryPath = propertiesParameters.getProperty("inputDirectory");
			String outputDirectoryPath = propertiesParameters.getProperty("outputDirectory");
			String hepatotoxicityJSONDict = propertiesParameters.getProperty("hepatotoxicityJSONDict");
			Integer index_id = new Integer(propertiesParameters.getProperty("index_id"));
			Integer index_text_to_tag = new Integer(propertiesParameters.getProperty("index_text_to_tag"));
			
			//curateDict(hepatotoxicityJSONDict, hepatotoxicityJSONDict + "_new.json");
			
			File inputDirectory = new File(inputDirectoryPath);
		    if(!inputDirectory.exists()) {
		    	return ;
		    }
		    if (!Files.isDirectory(Paths.get(inputDirectoryPath))) {
		    	return ;
		    }
		    File outputDirectory = new File(outputDirectoryPath);
		    if(!outputDirectory.exists())
		    	outputDirectory.mkdirs();
		    
		    String rulesPathOutput = "hepatotoxicity_rules.txt";
		    generateRulesForTagging(hepatotoxicityJSONDict, rulesPathOutput);
		    
			Properties props = new Properties();
			props.put("annotators", "tokenize, ssplit, regexner, entitymentions");
			props.put("regexner.mapping", rulesPathOutput);
			props.put("regexner.posmatchtype", "MATCH_ALL_TOKENS");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	
			List<String> filesProcessed = readFilesProcessed(outputDirectoryPath); 
		    BufferedWriter filesPrecessedWriter = new BufferedWriter(new FileWriter(outputDirectoryPath + File.separator + "list_files_processed.dat", true));
		    File[] files =  inputDirectory.listFiles();
			for (File file_to_classify : files) {
				if(file_to_classify.getName().endsWith(".txt") && filesProcessed!=null && !filesProcessed.contains(file_to_classify.getName())){
					taggingLog.info("Processing file  : " + file_to_classify.getName());
					String fileName = file_to_classify.getName();
					String outputFilePath = outputDirectory + File.separator + fileName;
					BufferedWriter outPutFile = new BufferedWriter(new FileWriter(outputFilePath));
					
					outPutFile.write("id\tstartOffset\tendOffset\ttext\tentityType\tetox_mapping_id\tmesh_omim_mapping_id\tmouse_pathology_mapping_id\t"
							+ "medra_mapping_id\tgemina_sympton_mapping_id\tdisease_ontology_mapping_id\tadverse_events_mapping_id\thuman_phenotype_mapping_id\tefpia_mapping_id\t" + 
				        	"mpheno_mapping_id\tcostart_concept\n");
					outPutFile.flush();
					for (String line : ObjectBank.getLineIterator(file_to_classify.getAbsolutePath(), "utf-8")) {
						try {
							String[] data = line.split("\t");
							String id =  data[index_id];
							String text =  data[index_text_to_tag];
							tagging(pipeline, id, text, outPutFile, file_to_classify.getName());
						}  catch (Exception e) {
							taggingLog.error("Error tagging the document line " + line + " belongs to the file: " +  fileName,e);
						} 
					
					}
					outPutFile.close();
					filesPrecessedWriter.write(file_to_classify.getName()+"\n");
					filesPrecessedWriter.flush();
				}
			}
			filesPrecessedWriter.close();
		}  catch (Exception e) {
			taggingLog.error("Generic error in the classification step",e);
		} 
	}


	/**
	 * Curate the hepatotoxicity Limtox Dictionary, run only one time to remove the duplicates original_entry now every thing is lowew case.
	 * @param inputPath
	 * @param outputPath
	 */
	private void curateDict(String inputPath, String outputPath) {
		List<LiverMarkerTerm> hepatotoxicityTerms = this.findAll(inputPath);
		Map<String, LiverMarkerTerm> hepatotoxicityTermsCurated = new HashMap<String, LiverMarkerTerm>();
		for (LiverMarkerTerm hepatotoxicityTerm : hepatotoxicityTerms) {
			if(hepatotoxicityTermsCurated.get(hepatotoxicityTerm.getOriginal_entry().toLowerCase())!=null) {
				taggingLog.warn("The key alreay exist : " + hepatotoxicityTerm.getOriginal_entry());
			}else {
				hepatotoxicityTermsCurated.put(hepatotoxicityTerm.getOriginal_entry().toLowerCase(), hepatotoxicityTerm);
			}
		}
		taggingLog.warn("Original Dict size : " + hepatotoxicityTerms.size() + ", curated Dict size : " + hepatotoxicityTermsCurated.size());
		generateJSONFile(hepatotoxicityTermsCurated.values(), outputPath);
		
	}

	@SuppressWarnings("unused")
	private void generateJSONFile(Collection<LiverMarkerTerm> hepatotoxicityTerms, String outputPath)  {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.setSerializationInclusion(Include.NON_NULL);
			objectMapper.setSerializationInclusion(Include.NON_EMPTY);
			try {
				objectMapper.writeValue(new File(outputPath), hepatotoxicityTerms);
			} catch (JsonGenerationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

	private Map<String, LiverMarkerTerm> generateRulesForTagging(String inputPath,String outputPath) throws IOException {
		List<LiverMarkerTerm> hepatotoxicityTerms = this.findAll(inputPath);
		BufferedWriter hepatotoxicityDictWriter = new BufferedWriter(new FileWriter(outputPath));
		for (LiverMarkerTerm hepatotoxicityTerm : hepatotoxicityTerms) {
			hepatotoxicityDictWriter.write(this.removeInvalidCharacters(hepatotoxicityTerm.getOriginal_entry()) + "\t" + "HEPATOTOXICITY\n");
			hepatotoxicityDictWriter.flush();
			hepatotoxicityTermsDict.put(hepatotoxicityTerm.getOriginal_entry(), hepatotoxicityTerm);
		}
		hepatotoxicityDictWriter.close();
		return hepatotoxicityTermsDict;
	}
	
	/**
	 * 
	 * @param original_entry
	 * @return
	 */
	private String removeInvalidCharacters(String original_entry) {
		// TODO Auto-generated method stub
		return original_entry.replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\*", "");
	}



	/**
	 * 
	 * @throws IOException
	 */
	protected String readJsonFile(String file_path) throws IOException {
		try{
			if(Files.isRegularFile(Paths.get(file_path))) {
				FileReader fr = new FileReader(file_path);
			    BufferedReader br = new BufferedReader(fr);
			
			    StringBuilder textBuilder = new StringBuilder();
			    String line;
			    while ((line=br.readLine())!=null) {
			    	textBuilder.append(line);
			    }
			    br.close();
			    fr.close();
			    return textBuilder.toString();
			}  else {
				
			}
	    }catch(IOException ex){
	       ex.printStackTrace();   
	    }
		return null;
	}
	
    private List<LiverMarkerTerm> findAll(String path) {
    	ObjectMapper mapper = new ObjectMapper();
    	mapper.configure(Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
		try {
			String json_string = readJsonFile(path);
			JsonNode rootNode = mapper.readTree(json_string);
			//JsonNode data = rootNode.path("hepatotoxicity");
			List<LiverMarkerTerm> myObjects = Arrays.asList(mapper.readValue(rootNode.toString(), LiverMarkerTerm[].class));
			return myObjects;
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();	
		}
		return null;
    }
	
	/**
	 * Findings of LTKB ChemicalCompunds
	 * 
	 * @param sourceId
	 * @param document_model
	 * @param first_finding_on_document
	 * @param section
	 * @param sentence_text
	 * @return
	 * @throws MoreThanOneEntityException
	 */
	private void tagging(StanfordCoreNLP pipeline, String id, String text_to_tag, BufferedWriter output, String fileName) {
//		String text = "Joe Smith was born in California. " +
//			      "In 2017, he went amineptine to Paris, France in the summer. " +
//			      "xenobiotic liver toxicity His flight left at 3:00pm on alatrofloxacin mesylate July 10th, 2017. " +
//			      "After eating some escargot for the first time, Joe said, \"That was delicious!\" " +
//			      "He sent xenobiotic liver toxicity a postcard to his sister Jane Smith. " +
//			      "After hearing about Joe's fipexide trip, Jane decided she might go to France one day.";
		//Annotation document = new Annotation(text_to_tag);
		Annotation document = new Annotation(text_to_tag.toLowerCase());
		// run all Annotators on this text
		pipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
        	// traversing the words in the current sentence
	        // a CoreLabel is a CoreMap with additional token-specific methods
        	
        	List<CoreMap> entityMentions = sentence.get(MentionsAnnotation.class);
    		for (CoreMap entityMention : entityMentions) {
    			try {
    				String keyword = entityMention.get(TextAnnotation.class);
        			String entityType = entityMention.get(CoreAnnotations.EntityTypeAnnotation.class);
			        if(entityType!=null && entityType.equals("HEPATOTOXICITY")) {
			        	CoreLabel token = entityMention.get(TokensAnnotation.class).get(0);
			        	LiverMarkerTerm hepatotoxicityTerm = hepatotoxicityTermsDict.get(keyword);
			        	if(hepatotoxicityTerm!=null) {
			        		output.write(id + "\t"+ token.beginPosition() + "\t" + (token.beginPosition() + keyword.length())  + "\t" + keyword + "\t" + entityType.toLowerCase() + "\t" + 
						        	hepatotoxicityTerm.getEtox_mapping_id() + "\t" + hepatotoxicityTerm.getMesh_omim_mapping_id() + "\t" + hepatotoxicityTerm.getMouse_pathology_mapping_id() + "\t" + 
						        	hepatotoxicityTerm.getMedDRA_mapping_id() + "\t" + hepatotoxicityTerm.getGemina_sympton_mapping_id() + "\t" + hepatotoxicityTerm.getDisease_ontology_mapping_id() + "\t" + 
						        	hepatotoxicityTerm.getAdverse_events_mapping_id() + "\t" + hepatotoxicityTerm.getHuman_phenotype_mapping_id() + "\t" + hepatotoxicityTerm.getEFPIA_mapping_id() + "\t" + 
						        	hepatotoxicityTerm.getMPheno_mapping_id() + "\t" + hepatotoxicityTerm.getCOSTART_concept() + "\n");
			        	} else {
			        		//revisar el tipo de ner que se esta utilizando hay casos en que encuentra cosas que no estan en el listado, creo que es porque se usa combinedner.
			        		output.write(id + "\t"+ token.beginPosition() + "\t" + (token.beginPosition() + keyword.length())  + "\t" + keyword + "\t" + entityType + "\t null \t null \t null \t null \t" + 
			        		 "null \t null \t null \t null \t null \t null \t null \n");
			        		taggingLog.warn("Entry not found " + keyword);
			        	}
			        	output.flush();
				    }
        		} catch (Exception e) {
					taggingLog.error("Generic Error tagging id "  + id + " in file " + fileName, e);
				}
    		}
        	
	        
        }
	}


	private List<String> readFilesProcessed(String outputDirectoryPath) {
		try {
			List<String> files_processed = new ArrayList<String>();
			if(Files.isRegularFile(Paths.get(outputDirectoryPath + File.separator + "list_files_processed.dat"))) {
				FileReader fr = new FileReader(outputDirectoryPath + File.separator + "list_files_processed.dat");
			    BufferedReader br = new BufferedReader(fr);
			    
			    String sCurrentLine;
			    while ((sCurrentLine = br.readLine()) != null) {
			    	files_processed.add(sCurrentLine);
				}
			    br.close();
			    fr.close();
			}
			return files_processed;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	  * Load Properties
	  * @param properitesParametersPath
	  */
	 public Properties loadPropertiesParameters(String properitesParametersPath) {
		 Properties prop = new Properties();
		 InputStream input = null;
		 try {
			 input = new FileInputStream(properitesParametersPath);
			 // load a properties file
			 prop.load(input);
			 return prop;
		 } catch (IOException ex) {
			 ex.printStackTrace();
		 } finally {
			 if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			 }
		}
		return null;
	 }	

	
}