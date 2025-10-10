package net.mysterria.stuff.features.recipes;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.*;

/**
 * Runtime recipe management system
 * Allows creating, removing, and managing custom recipes dynamically
 */
public class RecipeManager {

    private static final Map<String, NamespacedKey> customRecipes = new HashMap<>();
    private static RecipeManager instance;

    public RecipeManager() {
        instance = this;
    }

    public static RecipeManager getInstance() {
        return instance;
    }

    /**
     * Initialize the recipe system
     * Custom recipes can be added via commands or API
     */
    public void initialize() {
        PrettyLogger.debug("Recipe manager initialized - ready to accept custom recipes");
        // No default recipes to prevent game imbalance
        // Use /mystuff recipe commands or API to add recipes
    }

    /**
     * Register a recipe and track it
     */
    public boolean registerRecipe(Recipe recipe, String id) {
        try {
            // Check if recipes are enabled
            if (!MysterriaStuff.getInstance().getConfigManager().isRecipesEnabled()) {
                PrettyLogger.warn("Recipes are disabled in config!");
                return false;
            }

            // Check max recipe limit
            int maxRecipes = MysterriaStuff.getInstance().getConfigManager().getMaxRecipes();
            if (customRecipes.size() >= maxRecipes && !customRecipes.containsKey(id)) {
                PrettyLogger.warn("Maximum recipe limit reached (" + maxRecipes + ")");
                return false;
            }

            NamespacedKey key;

            if (recipe instanceof ShapedRecipe shapedRecipe) {
                key = shapedRecipe.getKey();
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                key = shapelessRecipe.getKey();
            } else {
                PrettyLogger.error("Unsupported recipe type: " + recipe.getClass().getName());
                return false;
            }

            // Remove if already exists
            if (customRecipes.containsKey(id)) {
                removeRecipe(id);
            }

            // Register the recipe
            boolean success = Bukkit.addRecipe(recipe);

            if (success) {
                customRecipes.put(id, key);

                // Log if configured
                if (MysterriaStuff.getInstance().getConfigManager().isLogRecipeChanges()) {
                    PrettyLogger.info("Registered custom recipe: " + id);
                }
                PrettyLogger.debug("Registered recipe: " + id);
                return true;
            } else {
                PrettyLogger.warn("Failed to register recipe: " + id);
                return false;
            }
        } catch (Exception e) {
            PrettyLogger.error("Error registering recipe " + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove a custom recipe
     */
    public boolean removeRecipe(String id) {
        NamespacedKey key = customRecipes.get(id);
        if (key == null) {
            PrettyLogger.warn("Recipe not found: " + id);
            return false;
        }

        boolean success = Bukkit.removeRecipe(key);
        if (success) {
            customRecipes.remove(id);

            // Log if configured
            if (MysterriaStuff.getInstance().getConfigManager().isLogRecipeChanges()) {
                PrettyLogger.info("Removed custom recipe: " + id);
            }
            PrettyLogger.debug("Removed recipe: " + id);
            return true;
        } else {
            PrettyLogger.warn("Failed to remove recipe: " + id);
            return false;
        }
    }

    /**
     * Create a shaped recipe at runtime
     */
    public boolean createShapedRecipe(String id, ItemStack result, String[] shape, Map<Character, Material> ingredients) {
        try {
            NamespacedKey key = new NamespacedKey(MysterriaStuff.getInstance(), id);
            ShapedRecipe recipe = new ShapedRecipe(key, result);

            // Validate shape
            if (shape.length < 1 || shape.length > 3) {
                PrettyLogger.error("Invalid shape: must be 1-3 rows");
                return false;
            }

            recipe.shape(shape);

            // Set ingredients
            for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            return registerRecipe(recipe, id);
        } catch (Exception e) {
            PrettyLogger.error("Error creating shaped recipe: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create a shapeless recipe at runtime
     */
    public boolean createShapelessRecipe(String id, ItemStack result, List<Material> ingredients) {
        try {
            NamespacedKey key = new NamespacedKey(MysterriaStuff.getInstance(), id);
            ShapelessRecipe recipe = new ShapelessRecipe(key, result);

            // Add ingredients
            for (Material ingredient : ingredients) {
                recipe.addIngredient(ingredient);
            }

            return registerRecipe(recipe, id);
        } catch (Exception e) {
            PrettyLogger.error("Error creating shapeless recipe: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get all custom recipe IDs
     */
    public Set<String> getCustomRecipeIds() {
        return new HashSet<>(customRecipes.keySet());
    }

    /**
     * Check if a recipe exists
     */
    public boolean hasRecipe(String id) {
        return customRecipes.containsKey(id);
    }

    /**
     * Get the number of custom recipes
     */
    public int getRecipeCount() {
        return customRecipes.size();
    }

    /**
     * Remove all custom recipes
     */
    public void removeAllRecipes() {
        PrettyLogger.debug("Removing all custom recipes...");
        List<String> ids = new ArrayList<>(customRecipes.keySet());
        for (String id : ids) {
            removeRecipe(id);
        }
        PrettyLogger.info("Removed all custom recipes");
    }

    /**
     * Reload all recipes
     */
    public void reloadRecipes() {
        PrettyLogger.debug("Reloading recipes...");
        removeAllRecipes();
        initialize();
        PrettyLogger.success("Recipes reloaded successfully!");
    }
}
