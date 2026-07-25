package org.gardin.gardinsadvancement.tabcreater;

public class Tab {
    private final String nameSpace;
    private final String name;
    private final String icon;
    private final String background;

    public Tab(String nameSpace, String name, String icon, String background) {
        this.nameSpace = nameSpace;
        this.name = name;
        this.icon = icon;
        this.background = background;
    }

    public String getNameSpace() {
        return this.nameSpace;
    }

    public String getName() {
        return this.name;
    }

    public String getIcon() {
        return this.icon;
    }

    public String getBackgroundTexture() {
        return this.background;
    }
}
