package interview.project.teahut;

public class InsufficientIngredientsException extends RuntimeException {

    private final String ingredientName;

    public InsufficientIngredientsException(String ingredientName) {
        super("Insufficient ingredient: " + ingredientName + ". Please refill.");
        this.ingredientName = ingredientName;
    }

    public String getIngredientName() {
        return ingredientName;
    }
}