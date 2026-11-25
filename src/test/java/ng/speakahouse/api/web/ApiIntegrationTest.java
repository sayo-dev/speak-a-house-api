package ng.speakahouse.api.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ApiIntegrationTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void fullVerticalSliceEndToEnd() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.contractVersion").value("1.0.0"))
                .andExpect(jsonPath("$.priceDataVerified").value(false));

        MvcResult sesRes = mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(containsString("ses_")))
                .andExpect(jsonPath("$.token").value(containsString("tok_")))
                .andReturn();
        JsonNode ses = mapper.readTree(sesRes.getResponse().getContentAsString());
        String sessionId = ses.get("sessionId").asText();
        String token = ses.get("token").asText();

        // Interpret utterance 1
        MvcResult int1 = mvc.perform(post("/api/v1/sessions/" + sessionId + "/interpret")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Three-bedroom bungalow on a 50 by 100 plot in Abeokuta, budget 50 million naira, shop at the front","baseVersionId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("spec"))
                .andExpect(jsonPath("$.needsConfirm").value(true))
                .andReturn();
        JsonNode specNode = mapper.readTree(int1.getResponse().getContentAsString()).get("spec");
        String confirmedSpec = specNode.toString().replace("\"unitConfirmed\":false", "\"unitConfirmed\":true");

        // Create version 1
        MvcResult ver1Res = mvc.perform(post("/api/v1/sessions/" + sessionId + "/versions")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":" + confirmedSpec + ",\"inputText\":\"Utterance 1\",\"parentVersionId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.estimate.expected").value(41393053))
                .andExpect(jsonPath("$.estimate.budget.status").value("within"))
                .andReturn();
        String ver1Id = mapper.readTree(ver1Res.getResponse().getContentAsString()).get("id").asText();

        // Interpret utterance 2 (patch)
        MvcResult int2 = mvc.perform(post("/api/v1/sessions/" + sessionId + "/interpret")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Add a boys' quarters at the back\",\"baseVersionId\":\"" + ver1Id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("patch"))
                .andReturn();
        String v2Spec = mapper.readTree(int2.getResponse().getContentAsString()).get("spec").toString();

        // Create version 2
        MvcResult ver2Res = mvc.perform(post("/api/v1/sessions/" + sessionId + "/versions")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":" + v2Spec + ",\"inputText\":\"Add BQ\",\"parentVersionId\":\"" + ver1Id + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(2))
                .andExpect(jsonPath("$.estimate.expected").value(47074508))
                .andExpect(jsonPath("$.diff.roomsAdded", hasSize(2)))
                .andReturn();
        String ver2Id = mapper.readTree(ver2Res.getResponse().getContentAsString()).get("id").asText();

        // List versions & move head back to ver1
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/versions")
                        .header("X-Session-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headVersionId").value(ver2Id))
                .andExpect(jsonPath("$.versions", hasSize(2)));

        mvc.perform(put("/api/v1/sessions/" + sessionId + "/head")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"" + ver1Id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headVersionId").value(ver1Id));

        // Input too long -> 413
        String tooLong = "a".repeat(501);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/interpret")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + tooLong + "\",\"baseVersionId\":null}"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LONG"));

        // Infeasible layout -> 422
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/versions")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spec":{"plot":{"width":40,"depth":60,"unit":"ft","unitConfirmed":true},"houseType":"bungalow","rooms":{"bedrooms":6,"ensuites":0,"dining":false},"features":{"shop":false,"bq":true,"garage":false,"fence":true},"roofing":"hip","finish":"standard","budget":null,"region":"abeokuta"},"inputText":"Too big","parentVersionId":null}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("LAYOUT_INFEASIBLE"))
                .andExpect(jsonPath("$.violations").isArray());
    }
}

