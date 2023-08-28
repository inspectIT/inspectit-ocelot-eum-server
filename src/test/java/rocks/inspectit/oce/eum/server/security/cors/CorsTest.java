package rocks.inspectit.oce.eum.server.security.cors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CorsTest {

    @Autowired
    private WebApplicationContext wac;

    public MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        DefaultMockMvcBuilder builder = MockMvcBuilders
                .webAppContextSetup(wac)
                .dispatchOptions(true);
        this.mockMvc = builder.build();
    }

    @Test
    public void testSuccessfulCors() throws Exception {
        this.mockMvc
                .perform(options("/beacon")
                        .header("Origin", "www.example.com")
                        .header("Access-Control-Request-Method", "GET")
                )
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    public void testFailingCors() throws Exception {
        this.mockMvc
                .perform(options("/beacon")
                        .header("Access-Control-Request-Method", "DUMMY")
                        .header("Origin", "www.example.com")
                )
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
