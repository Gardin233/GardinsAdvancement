package org.gardin.gardinsadvancement.tabcreater;

public class Tab {
    private final String nameSpace;
    private final String background;
    private final TabDisplayMode displayMode;

    public Tab(String nameSpace, String background, TabDisplayMode displayMode) {
        this.nameSpace = nameSpace;
        this.background = background;
        this.displayMode = displayMode;
    }

    public String getNameSpace() {
        return this.nameSpace;
    }

    public String getBackgroundTexture() {
        return this.background;
    }

    public TabDisplayMode getDisplayMode() {
        return displayMode;
    }
}
