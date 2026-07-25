package org.gardin.gardinsadvancement.advancementregister;

import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AdvancementData {
    private final String id;
    private final String title;
    private final ItemStack icon;
    private final AdvancementFrameType frame;
    private final boolean showToast;
    private final boolean announceChat;
    private final float x;
    private final float y;
    private final ChatColor color;
    private final List<String> description;

    public AdvancementData(
            String id,
            String title,
            ItemStack icon,
            AdvancementFrameType frame,
            boolean showToast,
            boolean announceChat,
            float x,
            float y,
            ChatColor color,
            List<String> description
    ) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.frame = frame;
        this.showToast = showToast;
        this.announceChat = announceChat;
        this.x = x;
        this.y = y;
        this.color = color;
        this.description = List.copyOf(description);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public AdvancementFrameType getFrame() {
        return frame;
    }

    public boolean isShowToast() {
        return showToast;
    }

    public boolean isAnnounceChat() {
        return announceChat;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public ChatColor getColor() {
        return color;
    }

    public List<String> getDescription() {
        return description;
    }

    public AdvancementDisplay createDisplay() {
        if (this.color == null) {
            return new AdvancementDisplay(
                    this.icon,
                    this.title,
                    this.frame,
                    this.showToast,
                    this.announceChat,
                    this.x,
                    this.y,
                    this.description
            );
        }
        return new AdvancementDisplay(
                this.icon,
                this.title,
                this.frame,
                this.showToast,
                this.announceChat,
                this.x,
                this.y,
                this.color,
                this.description
        );
    }
}
