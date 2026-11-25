package ng.speakahouse.api.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@TestPropertySource(properties = "app.admin-key=test-admin-secret")
@Transactional
class M3ApiIntegrationTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void boqCsvExportAndAdminPriceImportWork() throws Exception {
        MvcResult sesRes = mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode ses = mapper.readTree(sesRes.getResponse().getContentAsString());
        String sessionId = ses.get("sessionId").asText();
        String token = ses.get("token").asText();

        MvcResult verRes = mvc.perform(post("/api/v1/sessions/" + sessionId + "/versions")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spec":{"plot":{"width":50,"depth":100,"unit":"ft","unitConfirmed":true},"houseType":"bungalow","rooms":{"bedrooms":3,"ensuites":0,"dining":false},"features":{"shop":true,"bq":false,"garage":false,"fence":true},"roofing":"hip","finish":"standard","budget":{"amount":50000000,"currency":"NGN"},"region":"abeokuta"},"inputText":"Test","parentVersionId":null}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String vid = mapper.readTree(verRes.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/v1/sessions/" + sessionId + "/versions/" + vid + "/boq.csv")
                        .header("X-Session-Token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("boq-" + vid + ".csv")))
                .andExpect(content().string(containsString("key,group,description,quantity,unit,rate_ngn,amount_ngn")))
                .andExpect(content().string(containsString("concrete_m3")))
                .andExpect(content().string(containsString("Expected Total")));

        // Admin import without key -> 401
        mvc.perform(post("/api/v1/admin/prices/import")
                        .contentType("text/csv")
                        .content("price_key,grp,description,unit,region,finish,rate_ngn,source,supplier,observed_on,verified\n"))
                .andExpect(status().isUnauthorized());

        // Admin import with valid key: 1 valid row, 1 invalid row
        String csv = """
                price_key,grp,description,unit,region,finish,rate_ngn,source,supplier,observed_on,verified
                cement_bag,blockwork,Cement 50 kg (mortar and plaster),bag,abeokuta,any,11000,Dangote Depot Abeokuta,Abeokuta Builder Supply,2026-11-25,true
                bad_key,blockwork,Invalid zero rate,each,abeokuta,any,0,Unknown,,2026-11-25,false
                """;
        mvc.perform(post("/api/v1/admin/prices/import")
                        .header("X-Admin-Key", "test-admin-secret")
                        .contentType("text/csv")
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejected", hasSize(1)));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").exists());
    }
}
