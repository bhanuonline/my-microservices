1:
final ObjectMapper mapper = new ObjectMapper();
mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
List<LMGReturnCancelData> returnCancelDataList = null;
returnCancelDataList = mapper.readValue(cancelReturnJson,
TypeFactory.defaultInstance().constructCollectionType(List.class, LMGReturnCancelData.class));

ObjectMapper is Jackson's main class used for:

JSON → Java Object (Deserialization)
Java Object → JSON (Serialization)
mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
"Tell Jackson to convert the JSON array into a List where each element is an LMGReturnCancelData object (List<LMGReturnCancelData>)."