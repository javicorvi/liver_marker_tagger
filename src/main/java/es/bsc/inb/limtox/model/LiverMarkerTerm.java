package es.bsc.inb.limtox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class LiverMarkerTerm {
	
	private Integer keyId;
	
	private String marker_full_name;
	
	private String marker_namespace;
	
	private String marker_identifier;
	
	private String marker_normalization;
	
	private String concept_namespace;
	
	private String marker_type_name;

	public Integer getKeyId() {
		return keyId;
	}

	public void setKeyId(Integer keyId) {
		this.keyId = keyId;
	}

	public String getMarker_full_name() {
		return marker_full_name;
	}

	public void setMarker_full_name(String marker_full_name) {
		this.marker_full_name = marker_full_name;
	}

	public String getMarker_namespace() {
		return marker_namespace;
	}

	public void setMarker_namespace(String marker_namespace) {
		this.marker_namespace = marker_namespace;
	}

	public String getMarker_identifier() {
		return marker_identifier;
	}

	public void setMarker_identifier(String marker_identifier) {
		this.marker_identifier = marker_identifier;
	}

	public String getMarker_normalization() {
		return marker_normalization;
	}

	public void setMarker_normalization(String marker_normalization) {
		this.marker_normalization = marker_normalization;
	}

	public String getConcept_namespace() {
		return concept_namespace;
	}

	public void setConcept_namespace(String concept_namespace) {
		this.concept_namespace = concept_namespace;
	}

	public String getMarker_type_name() {
		return marker_type_name;
	}

	public void setMarker_type_name(String marker_type_name) {
		this.marker_type_name = marker_type_name;
	}
	
	public void toLowerCase() {
		if(marker_full_name!=null) {
			marker_full_name=marker_full_name.toLowerCase();
		}
	}
	
	@Override
    public boolean equals(Object obj) {
	    if (obj == null) {
	        return false;
	    }else if(marker_full_name==null || ((LiverMarkerTerm)obj).marker_full_name==null) {
	    	return false;
	    }else {
	    	if(marker_full_name.equals(((LiverMarkerTerm)obj).marker_full_name)) {
	    		return true;
	    	}
	    	return false;
	    }
	    
	}

	@Override
	public int hashCode() {
	    return marker_full_name.hashCode();
	}
   
}
