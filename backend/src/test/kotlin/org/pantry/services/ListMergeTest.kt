package org.pantry.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pantry.models.Item

class ListMergeTest {

    private fun item(name: String, quantity: Double, unit: String? = null, isChecked: Boolean = false, fromRecipes: String? = null) =
        Item(id = name + unit + isChecked, name = name, quantity = quantity, isChecked = isChecked, unit = unit, fromRecipes = fromRecipes)

    private fun add(name: String, amount: Double?, unit: String? = null, recipe: String = "Bolognese") =
        IngredientToAdd(name, amount, unit, recipe)

    @Test
    fun `new ingredients become new items, noting the recipe`() {
        val plan = ListMerge.plan(emptyList(), listOf(add("minced beef", 500.0, "g"), add("onion", 1.0), add("salt", null)))

        assertTrue(plan.changes.isEmpty())
        assertEquals(
            listOf(
                NewItem("minced beef", 500.0, "g", "Bolognese"),
                NewItem("onion", 1.0, null, "Bolognese"),
                NewItem("salt", 1.0, null, "Bolognese")
            ),
            plan.newItems
        )
    }

    @Test
    fun `same name and unit are added to the unticked item on the list`() {
        val milk = item("Milk", 0.5, "l")
        val plan = ListMerge.plan(listOf(milk), listOf(add("milk", 250.0, "ml", "Pancakes")))

        assertEquals(listOf(ItemChange(milk.id, 750.0, "ml", "Pancakes")), plan.changes)
        assertTrue(plan.newItems.isEmpty())
    }

    @Test
    fun `grams switch to kilograms from 1000`() {
        val plan = ListMerge.plan(
            emptyList(),
            listOf(add("flour", 600.0, "g"), add("flour", 0.5, "kg", "Bread"))
        )

        assertEquals(listOf(NewItem("flour", 1.1, "kg", "Bolognese, Bread")), plan.newItems)
    }

    @Test
    fun `counts round up, spoons keep two decimals`() {
        val plan = ListMerge.plan(
            emptyList(),
            listOf(add("egg", 1.5), add("garlic", 2.25, "clove"), add("olive oil", 1.3333, "tbsp"), add("butter", 12.2, "g"))
        )

        assertEquals(listOf(2.0, 3.0, 1.33, 13.0), plan.newItems.map { it.quantity })
    }

    @Test
    fun `floating point noise doesn't round a whole count up`() {
        val plan = ListMerge.plan(emptyList(), listOf(add("egg", 0.1 + 0.2 + 0.7 + 1.0)))

        assertEquals(2.0, plan.newItems.single().quantity)
    }

    @Test
    fun `different units and ticked items stay separate`() {
        val tickedFlour = item("flour", 1.0, "kg", isChecked = true)
        val cupFlour = item("flour", 2.0, "cup")
        val plan = ListMerge.plan(listOf(tickedFlour, cupFlour), listOf(add("flour", 200.0, "g")))

        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf(NewItem("flour", 200.0, "g", "Bolognese")), plan.newItems)
    }

    @Test
    fun `a plain item typed by hand takes the recipe's amount and unit`() {
        val milk = item("milk", 1.0)
        val twoOnions = item("onion", 2.0)
        val plan = ListMerge.plan(listOf(milk, twoOnions), listOf(add("milk", 500.0, "ml"), add("onion", 300.0, "g")))

        assertEquals(listOf(ItemChange(milk.id, 500.0, "ml", "Bolognese")), plan.changes)
        // "2 onions" says how many: the grams are a separate line
        assertEquals(listOf(NewItem("onion", 300.0, "g", "Bolognese")), plan.newItems)
    }

    @Test
    fun `unitless counts add up with a plain item typed by hand`() {
        val onions = item("Onion", 1.0)
        val plan = ListMerge.plan(listOf(onions), listOf(add("onion", 2.0)))

        assertEquals(listOf(ItemChange(onions.id, 3.0, null, "Bolognese")), plan.changes)
    }

    @Test
    fun `ingredients without an amount are added once, or only noted when already there`() {
        val salt = item("salt", 1.0)
        val plan = ListMerge.plan(
            listOf(salt),
            listOf(add("salt", null), add("black pepper", null), add("black pepper", null, recipe = "Soup"), add("parmesan", null), add("parmesan", 50.0, "g"))
        )

        assertEquals(listOf(ItemChange(salt.id, 1.0, null, "Bolognese")), plan.changes)
        assertEquals(
            listOf(NewItem("parmesan", 50.0, "g", "Bolognese"), NewItem("black pepper", 1.0, null, "Bolognese, Soup")),
            plan.newItems
        )
    }

    @Test
    fun `recipe names already noted aren't repeated`() {
        val beef = item("minced beef", 500.0, "g", fromRecipes = "Bolognese")
        val plan = ListMerge.plan(listOf(beef), listOf(add("minced beef", 500.0, "g")))

        assertEquals(listOf(ItemChange(beef.id, 1.0, "kg", "Bolognese")), plan.changes)
    }
}
