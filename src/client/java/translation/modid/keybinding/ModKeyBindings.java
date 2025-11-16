package translation.modid.keybinding;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyMapping openConfig;
    public static KeyMapping clearSignCache;
    
    public static void register() {
        openConfig = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simple-translation.config",
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.simple-translation"
        ));
        
        clearSignCache = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simple-translation.clear_sign_cache",
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.simple-translation"
        ));
    }
}

