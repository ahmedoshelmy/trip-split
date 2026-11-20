package dev.tripsplit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The main flow end to end against a real Postgres (Testcontainers): HTTP commands append events,
 * the inline projection answers balances, replay answers "as of", the outbox-fed timeline catches
 * up asynchronously.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TripFlowIntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private String tripUrl;
  private List<String> members;

  @BeforeEach
  void createTrip() throws Exception {
    var created =
        send(
            post("/api/trips"),
            null,
            """
            {"name": "Fayoum day trip", "baseCurrency": "EGP", "members": ["Omar", "Lina", "Karim"]}
            """,
            status().isCreated());
    tripUrl = "/api/trips/" + created.get("id").asString();
    members = new ArrayList<>();
    created.get("members").forEach(m -> members.add(m.get("id").asString()));
  }

  @Test
  void expensesUpdateTheBalancesProjectionAndTheSumIsZero() throws Exception {
    addEqualExpense("Lunch", 0, "300.00");
    addEqualExpense("Fuel", 1, "100.00");
    send(
        post(tripUrl + "/expenses"),
        null,
        """
        {"description": "Boat", "paidBy": "%s", "amount": "20.00", "currency": "USD",
         "rate": "50", "split": {"kind": "SHARES", "shares": {"%s": 1, "%s": 3}}}
        """
            .formatted(members.get(2), members.get(0), members.get(2)),
        status().isCreated());

    var sheet = getJson(tripUrl + "/balances");

    // Omar: +300 -100 -33.34 -250 = -83.34 (the fuel's leftover piaster goes to the first joiner)
    // Lina: +100 -100 -33.33 = -33.33 ; Karim: +1000 (20 USD at 50) -100 -33.33 -750 = 116.67
    assertThat(balances(sheet)).containsExactly(-8334L, -3333L, 11667L);
    assertThat(sheet.get("version").asLong()).isEqualTo(7);

    var settle = getJson(tripUrl + "/settle-up");
    assertThat(settle.get("transfers")).hasSize(2);
  }

  @Test
  void asOfReplaysTheStreamUpToThatInstant() throws Exception {
    addEqualExpense("Lunch", 0, "300.00");
    var between = Instant.now();
    Thread.sleep(20);
    addEqualExpense("Hotel", 1, "900.00");

    var then = getJson(tripUrl + "/balances?asOf=" + between);
    var now = getJson(tripUrl + "/balances");

    assertThat(balances(then)).containsExactly(20000L, -10000L, -10000L);
    assertThat(balances(now)).containsExactly(-10000L, 50000L, -40000L);
    assertThat(then.get("asOf").asString()).isNotBlank();
  }

  @Test
  void voidingAnExpenseIsCompensatedAndShowsUpInTheTimeline() throws Exception {
    var expenseId = addEqualExpense("Lunch", 0, "300.00");

    send(
        post(tripUrl + "/expenses/" + expenseId + "/void"),
        "\"5\"",
        "{\"reason\": \"paid twice\"}",
        status().isOk());

    assertThat(balances(getJson(tripUrl + "/balances"))).containsOnly(0L);
    var events = getJson(tripUrl + "/events");
    assertThat(events.get(events.size() - 1).get("type").asString())
        .isEqualTo("trip.expense-voided.v1");
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var timeline = getJson(tripUrl + "/timeline");
          assertThat(timeline).hasSize(6);
          assertThat(timeline.get(0).get("kind").asString()).isEqualTo("EXPENSE_VOIDED");
          assertThat(timeline.get(1).get("undoable").asBoolean()).isFalse();
          assertThat(timeline.get(1).get("undoneAt")).isNotNull();
        });
  }

  @Test
  void staleIfMatchIsRejectedWith412AndCurrentEtagIsReturned() throws Exception {
    mvc.perform(get(tripUrl)).andExpect(header().string("ETag", "\"4\""));

    addEqualExpense("Lunch", 0, "300.00");
    send(
        post(tripUrl + "/members"),
        "\"4\"",
        "{\"name\": \"Nour\"}",
        status().isPreconditionFailed());

    send(post(tripUrl + "/members"), "\"5\"", "{\"name\": \"Nour\"}", status().isCreated());
  }

  @Test
  void ruleViolationsAreProblemDetails() throws Exception {
    var problem =
        send(
            post(tripUrl + "/expenses"),
            null,
            """
            {"description": "Gift", "paidBy": "%s", "amount": "10.005", "currency": "EGP",
             "split": {"kind": "EQUAL", "participants": ["%s"]}}
            """
                .formatted(members.get(0), members.get(0)),
            status().isUnprocessableContent());
    assertThat(problem.get("detail").asString()).contains("at most 2 decimals");

    mvc.perform(get("/api/trips/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  private String addEqualExpense(String description, int payer, String amount) throws Exception {
    var body =
        """
        {"description": "%s", "paidBy": "%s", "amount": "%s", "currency": "EGP",
         "split": {"kind": "EQUAL", "participants": ["%s", "%s", "%s"]}}
        """
            .formatted(
                description,
                members.get(payer),
                amount,
                members.get(0),
                members.get(1),
                members.get(2));
    return send(post(tripUrl + "/expenses"), null, body, status().isCreated()).get("id").asString();
  }

  private JsonNode send(
      MockHttpServletRequestBuilder request, String ifMatch, String body, ResultMatcher expected)
      throws Exception {
    if (ifMatch != null) {
      request.header("If-Match", ifMatch);
    }
    var response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(response);
  }

  private JsonNode getJson(String url) throws Exception {
    return JSON.readTree(
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static List<Long> balances(JsonNode sheet) {
    var values = new ArrayList<Long>();
    sheet.get("balances").forEach(b -> values.add(b.get("balanceMinor").asLong()));
    return values;
  }
}
