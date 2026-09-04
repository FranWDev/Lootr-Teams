import java.lang.reflect.Method;
import noobanidus.mods.lootr.common.data.LootrSavedData;

public class PrintMethods {
    public static void main(String[] args) {
        for (Method m : LootrSavedData.class.getDeclaredMethods()) {
            System.out.println(m.getName());
            for (Class<?> param : m.getParameterTypes()) {
                System.out.println("  " + param.getName());
            }
        }
    }
}
