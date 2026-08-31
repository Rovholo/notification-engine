//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import java.util.HashMap;
//import java.util.Map;
//
//public abstract class EntityBaseDto {
//    public static final String K_ID = "id";
//
//    private String id;
//
//    public EntityBaseDto() {}
//
//    public EntityBaseDto(Map<String, Object> map) {
//        this.id = (String) map.get(K_ID);
//    }
//
//    public EntityBaseDto(String json) throws JsonProcessingException {
//        this(new ObjectMapper().readValue(json, Map.class));
//    }
//
//    public String getId() {
//        return id;
//    }
//
//    public void setId(String id) {
//        this.id = id;
//    }
//
//    // Abstract method to be implemented by subclasses
//    public abstract Map<String, Object> toMap();
//
//    public Map<String, Object> toFullMap() {
//        Map<String, Object> fullMap = new HashMap<>();
//        fullMap.put(K_ID, id);
//        fullMap.putAll(toMap());
//        return fullMap;
//    }
//
//    public String toFullString() throws JsonProcessingException {
//        return new ObjectMapper().writeValueAsString(toFullMap());
//    }
//
//    @Override
//    public String toString() {
//        try {
//            return new ObjectMapper().writeValueAsString(toMap());
//        } catch (JsonProcessingException e) {
//            return "{}";
//        }
//    }
//}
