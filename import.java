package io.github.microcks.importer.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.*;
import io.github.microcks.repository.TestCaseRepository;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenAPIImporter {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIImporter.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private ObjectMapper mapper;

    public void importTestCasesFromOpenAPI(String serviceId, Paths paths) {
        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String pathKey = entry.getKey();
            PathItem pathItem = entry.getValue();

            for (PathItem.HttpMethod httpMethod : PathItem.HttpMethod.values()) {
                Operation operation = pathItem.readOperationsMap().get(httpMethod);
                if (operation == null) continue;

                if (operation.getExtensions() != null && operation.getExtensions().containsKey("x-microcks-testcases")) {
                    Object extension = operation.getExtensions().get("x-microcks-testcases");
                    JsonNode testCasesNode = mapper.valueToTree(extension);

                    if (testCasesNode.isArray()) {
                        for (JsonNode node : testCasesNode) {
                            try {
                                TestCase testCase = new TestCase();
                                testCase.setName(node.path("name").asText());
                                testCase.setApiId(serviceId);
                                testCase.setApiPath(pathKey);
                                testCase.setMethod(httpMethod.name());
                                testCase.setOperationId(operation.getOperationId());

                                // Request block
                                JsonNode requestNode = node.path("request");
                                TestRequest request = new TestRequest();
                                if (requestNode.has("headers")) {
                                    request.setHeaders(mapper.convertValue(requestNode.get("headers"), new TypeReference<Map<String, String>>() {}));
                                }
                                if (requestNode.has("queryParameters")) {
                                    request.setQueryParameters(mapper.convertValue(requestNode.get("queryParameters"), new TypeReference<Map<String, String>>() {}));
                                }
                                if (requestNode.has("body")) {
                                    request.setBody(requestNode.get("body").toString());
                                }
                                testCase.setRequest(request);

                                // Expected block
                                JsonNode expectedNode = node.path("expected");
                                TestResponse expected = new TestResponse();
                                expected.setStatus(expectedNode.path("status").asInt());
                                if (expectedNode.has("headers")) {
                                    expected.setHeaders(mapper.convertValue(expectedNode.get("headers"), new TypeReference<Map<String, String>>() {}));
                                }
                                if (expectedNode.has("body")) {
                                    expected.setBody(expectedNode.get("body").toString());
                                }
                                testCase.setExpected(expected);

                                // Script and engine
                                if (node.has("script")) {
                                    testCase.setAssertScript(node.path("script").asText());
                                }
                                if (node.has("scriptEngine")) {
                                    testCase.setScriptEngine(node.path("scriptEngine").asText());
                                } else {
                                    testCase.setScriptEngine("javascript"); // default
                                }

                                testCaseRepository.save(testCase);

                            } catch (Exception e) {
                                log.warn("Failed to parse x-microcks-testcase: {}", e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }
}  
