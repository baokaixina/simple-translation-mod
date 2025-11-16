package translation.modid.integration;

// Mod Menu API imports - these are optional dependencies
// The mod will work without Mod Menu installed, but this integration
// allows the config screen to be opened from Mod Menu
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import translation.modid.screen.ConfigScreen;

/**
 * Mod Menu 集成类
 * 允许在 Mod Menu 中打开配置界面，而不仅仅是在存档内
 * 
 * 注意：这是一个可选集成。如果 Mod Menu 未安装，此 entrypoint 不会被调用。
 * 模组仍然可以通过按键绑定在存档内打开配置界面。
 */
public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent);
    }
}

