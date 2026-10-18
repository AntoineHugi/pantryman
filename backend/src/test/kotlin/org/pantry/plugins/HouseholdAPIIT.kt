package org.pantry.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.component.inject
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.CreateHouseholdRequest
import org.pantry.models.CreateItemRequest
import org.pantry.models.GroceryList
import org.pantry.models.Household
import org.pantry.models.JoinHouseholdRequest
import org.pantry.models.RenameHouseholdRequest
import org.pantry.models.UpdateListHouseholdRequest
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.postgres.tables.ItemTable
import org.pantry.postgres.tables.MealPlanEntryTable
import org.pantry.postgres.tables.MealPlanTable
import org.pantry.postgres.tables.PantryItemTable
import org.pantry.postgres.tables.RecipeIngredientTable
import org.pantry.postgres.tables.RecipeStepTable
import org.pantry.postgres.tables.RecipeTable
import org.pantry.postgres.tables.RecipeTagTable
import org.pantry.postgres.tables.UserTable
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.PantryRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.GroceryListService
import org.pantry.services.HouseholdService
import org.pantry.services.PantryService
import org.pantry.services.ItemService
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HouseholdAPIIT : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { ItemRepository() }
                single { ItemService(get()) }
                single { GroceryListRepository(get()) }
                single { GroceryListService(get()) }
                single { HouseholdRepository() }
                single { HouseholdService(get()) }
                single { PantryRepository() }
                single { PantryService(get(), get()) }
            })
    }

    private val groceryListRepo: GroceryListRepository by inject()
    private val householdRepo: HouseholdRepository by inject()

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val eve = UUID.randomUUID()

    @BeforeEach
    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable,
                RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable, MealPlanTable, MealPlanEntryTable, PantryItemTable
            )
            ItemTable.deleteAll()
            GroceryListTable.deleteAll()
            HouseholdMemberTable.deleteAll()
            HouseholdTable.deleteAll()
        }
        listOf(alice, bob, eve).forEach { UserRepository().create(it, "$it@example.com", "hash", UUID.randomUUID()) }
    }

    private fun token(userId: UUID): String = JWT.create()
        .withAudience("test-audience")
        .withIssuer("test-issuer")
        .withClaim("userId", userId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + 60000))
        .sign(Algorithm.HMAC256("test-secret-key-for-testing-only"))

    private fun ApplicationTestBuilder.setupTestApplication() {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "test-secret-key-for-testing-only",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience"
            )
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            configureStatusPages()
            configureSecurity()
            configureRateLimiting()
            groceriesApi()
            householdsApi()
        }
    }

    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private suspend fun ApplicationTestBuilder.createHousehold(user: UUID, name: String = "Home"): Household {
        val response = jsonClient.post("/households") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(CreateHouseholdRequest(name))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun ApplicationTestBuilder.join(user: UUID, code: String): HttpResponse =
        jsonClient.post("/households/join") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(JoinHouseholdRequest(code))
        }

    private suspend fun ApplicationTestBuilder.share(user: UUID, listId: String, householdId: String?): HttpResponse =
        jsonClient.put("/lists/$listId/household") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(UpdateListHouseholdRequest(householdId))
        }

    private suspend fun ApplicationTestBuilder.lists(user: UUID): List<GroceryList> =
        jsonClient.get("/lists") { bearerAuth(token(user)) }.body()

    @Test
    fun `create household makes the creator its only member`() = testApplication {
        setupTestApplication()

        val household = createHousehold(alice, "  Home  ")

        assertEquals("Home", household.name)
        assertEquals(8, household.inviteCode.length)
        assertEquals(listOf(alice.toString()), household.members.map { it.userId })
        assertEquals(alice.toString(), household.ownerId)

        val mine: List<Household> = jsonClient.get("/households") { bearerAuth(token(alice)) }.body()
        assertEquals(listOf(household.id), mine.map { it.id })
        val bobs: List<Household> = jsonClient.get("/households") { bearerAuth(token(bob)) }.body()
        assertTrue(bobs.isEmpty())
    }

    @Test
    fun `blank household name returns 400`() = testApplication {
        setupTestApplication()

        val response = jsonClient.post("/households") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(CreateHouseholdRequest(" "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `join with invite code adds member and ignores case and dashes`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        val code = household.inviteCode
        val typed = code.substring(0, 4).lowercase() + "-" + code.substring(4)

        val response = join(bob, typed)

        assertEquals(HttpStatusCode.OK, response.status)
        val joined: Household = response.body()
        assertEquals(listOf(alice.toString(), bob.toString()), joined.members.map { it.userId })

        // Joining again is a no-op
        assertEquals(HttpStatusCode.OK, join(bob, code).status)
        assertEquals(2, householdRepo.getForMember(UUID.fromString(household.id), bob)!!.members.size)
    }

    @Test
    fun `join with unknown code returns 404`() = testApplication {
        setupTestApplication()
        createHousehold(alice)

        assertEquals(HttpStatusCode.NotFound, join(bob, "NOPE2345").status)
    }

    @Test
    fun `list shared with household is visible and editable by members only`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)

        val created = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(CreateGroceryListRequest("Weekly", household.id))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val list: GroceryList = created.body()
        assertEquals(household.id, list.householdId)

        // Bob sees it and can add items
        assertEquals(listOf(list.id), lists(bob).map { it.id })
        val addItem = jsonClient.post("/lists/${list.id}/items") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(bob))
            setBody(CreateItemRequest("milk", 1.0))
        }
        assertEquals(HttpStatusCode.Created, addItem.status)

        // Eve, not a member, can't see or touch it
        assertTrue(lists(eve).isEmpty())
        assertEquals(HttpStatusCode.NotFound, jsonClient.get("/lists/${list.id}") { bearerAuth(token(eve)) }.status)
        assertEquals(HttpStatusCode.Forbidden, jsonClient.get("/lists/${list.id}/items") { bearerAuth(token(eve)) }.status)
        assertEquals(HttpStatusCode.NotFound, jsonClient.delete("/lists/${list.id}") { bearerAuth(token(eve)) }.status)
        assertEquals(HttpStatusCode.NotFound, share(eve, list.id, null).status)
    }

    @Test
    fun `personal lists stay private to their owner`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        groceryListRepo.create(UUID.randomUUID(), "Alice only", alice)

        assertTrue(lists(bob).isEmpty())
    }

    @Test
    fun `cannot create a list in a household you are not in`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        val response = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(eve))
            setBody(CreateGroceryListRequest("Sneaky", household.id))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(lists(alice).isEmpty())
    }

    @Test
    fun `owner can share and unshare a list`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        val list = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice)

        val shared = share(alice, list.id, household.id)
        assertEquals(HttpStatusCode.OK, shared.status)
        assertEquals(household.id, shared.body<GroceryList>().householdId)
        assertEquals(listOf(list.id), lists(bob).map { it.id })

        val unshared = share(alice, list.id, null)
        assertEquals(HttpStatusCode.OK, unshared.status)
        assertNull(unshared.body<GroceryList>().householdId)
        assertTrue(lists(bob).isEmpty())
    }

    @Test
    fun `members who are not the owner cannot change sharing`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        val list = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice, UUID.fromString(household.id))

        assertEquals(HttpStatusCode.Forbidden, share(bob, list.id, null).status)
        assertEquals(household.id, groceryListRepo.getById(UUID.fromString(list.id), alice)!!.householdId)
    }

    @Test
    fun `cannot share a list with a household you are not in`() = testApplication {
        setupTestApplication()
        val evesHousehold = createHousehold(eve)
        val list = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice)

        assertEquals(HttpStatusCode.Forbidden, share(alice, list.id, evesHousehold.id).status)
        assertEquals(HttpStatusCode.BadRequest, share(alice, list.id, "not-a-uuid").status)
        assertTrue(lists(eve).isEmpty())
    }

    @Test
    fun `leaving hands shared lists to the remaining members`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        val shared = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice, UUID.fromString(household.id))

        val response = jsonClient.post("/households/${household.id}/leave") { bearerAuth(token(alice)) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(lists(alice).isEmpty())
        val bobsLists = lists(bob)
        assertEquals(listOf(shared.id), bobsLists.map { it.id })
        assertEquals(bob.toString(), bobsLists.single().userId)
        // Bob owns it now, so he can unshare it
        assertEquals(HttpStatusCode.OK, share(bob, shared.id, null).status)
    }

    @Test
    fun `last member leaving deletes the household and keeps the lists as personal`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        val shared = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice, UUID.fromString(household.id))

        jsonClient.post("/households/${household.id}/leave") { bearerAuth(token(alice)) }

        assertNull(householdRepo.getIdByInviteCode(household.inviteCode))
        val list = lists(alice).single()
        assertEquals(shared.id, list.id)
        assertNull(list.householdId)
    }

    @Test
    fun `leaving a household you are not in returns 404`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        val response = jsonClient.post("/households/${household.id}/leave") { bearerAuth(token(eve)) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(1, householdRepo.getForMember(UUID.fromString(household.id), alice)!!.members.size)
    }

    @Test
    fun `regenerating the invite code invalidates the old one`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        val response = jsonClient.post("/households/${household.id}/invite-code") { bearerAuth(token(alice)) }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated: Household = response.body()
        assertNotEquals(household.inviteCode, updated.inviteCode)
        assertEquals(HttpStatusCode.NotFound, join(bob, household.inviteCode).status)
        assertEquals(HttpStatusCode.OK, join(bob, updated.inviteCode).status)
    }

    @Test
    fun `non-members cannot regenerate the invite code`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        val response = jsonClient.post("/households/${household.id}/invite-code") { bearerAuth(token(eve)) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNotNull(householdRepo.getIdByInviteCode(household.inviteCode))
    }

    @Test
    fun `account deletion hands shared lists to the household`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        val shared = groceryListRepo.create(UUID.randomUUID(), "Weekly", alice, UUID.fromString(household.id))
        groceryListRepo.create(UUID.randomUUID(), "Alice only", alice)

        householdRepo.removeMemberFromAll(alice)
        UserRepository().delete(alice)

        assertEquals(listOf(shared.id), lists(bob).map { it.id })
        assertEquals(listOf(bob.toString()), householdRepo.getForUser(bob).single().members.map { it.userId })
    }

    private suspend fun ApplicationTestBuilder.rename(user: UUID, householdId: String, name: String): HttpResponse =
        jsonClient.patch("/households/$householdId") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(RenameHouseholdRequest(name))
        }

    private suspend fun ApplicationTestBuilder.removeMember(user: UUID, householdId: String, memberId: UUID): HttpResponse =
        jsonClient.delete("/households/$householdId/members/$memberId") { bearerAuth(token(user)) }

    @Test
    fun `owner can rename the household`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        val response = rename(alice, household.id, " Flat ")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Flat", response.body<Household>().name)
        assertEquals(HttpStatusCode.BadRequest, rename(alice, household.id, " ").status)
    }

    @Test
    fun `only the owner can rename the household`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)

        assertEquals(HttpStatusCode.Forbidden, rename(bob, household.id, "Bob's").status)
        assertEquals(HttpStatusCode.NotFound, rename(eve, household.id, "Eve's").status)
        assertEquals("Home", householdRepo.getForMember(UUID.fromString(household.id), alice)!!.name)
    }

    @Test
    fun `owner can remove a member, who loses access and can't rejoin with the old code`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        val bobsShared = groceryListRepo.create(UUID.randomUUID(), "Bob's", bob, UUID.fromString(household.id))

        val response = removeMember(alice, household.id, bob)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(lists(bob).isEmpty())
        // The household keeps the list; the owner takes it over
        val list = lists(alice).single()
        assertEquals(bobsShared.id, list.id)
        assertEquals(alice.toString(), list.userId)
        // The invite code changed
        assertEquals(HttpStatusCode.NotFound, join(bob, household.inviteCode).status)
        val updated = householdRepo.getForMember(UUID.fromString(household.id), alice)!!
        assertEquals(listOf(alice.toString()), updated.members.map { it.userId })
        assertNotEquals(household.inviteCode, updated.inviteCode)
    }

    @Test
    fun `only the owner can remove members`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        join(eve, household.inviteCode)

        assertEquals(HttpStatusCode.Forbidden, removeMember(bob, household.id, eve).status)
        assertEquals(HttpStatusCode.Forbidden, removeMember(bob, household.id, alice).status)
        assertEquals(3, householdRepo.getForMember(UUID.fromString(household.id), alice)!!.members.size)
    }

    @Test
    fun `owner cannot remove themselves or non-members`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)

        assertEquals(HttpStatusCode.BadRequest, removeMember(alice, household.id, alice).status)
        assertEquals(HttpStatusCode.NotFound, removeMember(alice, household.id, eve).status)
        assertEquals(HttpStatusCode.NotFound, removeMember(eve, household.id, alice).status)
    }

    @Test
    fun `when the owner leaves, the longest-standing member becomes owner`() = testApplication {
        setupTestApplication()
        val household = createHousehold(alice)
        join(bob, household.inviteCode)
        join(eve, household.inviteCode)

        jsonClient.post("/households/${household.id}/leave") { bearerAuth(token(alice)) }

        val updated = householdRepo.getForMember(UUID.fromString(household.id), bob)!!
        assertEquals(bob.toString(), updated.ownerId)
        assertEquals(HttpStatusCode.OK, rename(bob, household.id, "Bob's").status)
        assertEquals(HttpStatusCode.Forbidden, rename(eve, household.id, "Eve's").status)
    }
}
