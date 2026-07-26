# Gardin's Advancement
![GitHub License](https://img.shields.io/github/license/Gardin233/GardinsAdvancement)

您不想写自己的数据包：那对于您来说太过复杂，所以您想要一个插件，通过更友善的yml将自定义的原生风格的进度添加进你的服务器。

但一直以来我们的进度都依赖于插件开发者制作的事件监听器，这实际上对用户产生了一些限制：

传统成就插件通常依赖事件监听器判断玩家行为。

这种方式存在一个问题: 当一个插件添加新的物品、技能或系统时，如果没有专门适配，系统无法识别这些数据。

现如今placeholder几乎充斥在所有的插件里，越来越多的插件选择它作为暴露内部状态的工具

所以使用它作为条件再好不过了，既能保持成就插件本身轻量，又能天然兼容所有提供 Placeholder 的插件。






## Gardin's Advancement 正是基于这一理念设计的。
Gardin's Advancement 使用 PlaceholderAPI 作为统一数据接口。 

插件本身不依赖大量事件监听器，而是使用 Placeholder 表达式作为统一的条件系统。只要某个插件提供了 Placeholder，就可以直接参与成就判定，无需等待额外的适配。

例如：
```yml
- "placeholder: %player_level% >= 15 && %townyadvanced_player_jailed% == true"
```
上面的配置表示：

玩家等级达到 15 级或以上；
玩家当前在 Towny 中处于监禁状态。

只有两个条件同时满足，玩家才会获得对应的成就。
由于采用了这种设计，理论上任何支持 PlaceholderAPI 的插件都可以直接成为成就条件来源。

对于复杂逻辑，可以结合 PlaceholderAPI 的 JS 扩展或其他脚本插件进行封装。

我们支持如下表达式:
- `!`
- `<`
- `>`
- `==`
- `>=`
- `<=`
- `&&`
- `||`
- `()`



一个示例文件如下
```yml
tabs:
  novice_path:
    display-mode: direct
    background: "minecraft:textures/block/stone.png"
    advancements:
      novice_root:
        type: root
        conditions:
          - "placeholder: %player_level% >= 0"
        data: #minecraft每个标签页下仅支持一个根进度，并且以根进度作为页面标题，以根进度icon作为标签页icon
          title: "初入世界"
          icon: minecraft:acacia_door
          frame: task
          show_toast: true
          announce_chat: true
          x: 10
          y: 20
          description:
            - "这是新手线路的根节点"
      novice_step_one:
        type: common
        parent: novice_root
        conditions:
          - "placeholder: %player_level% >= 5"
        data:
          title: "迈出第一步"
          icon: CE:customfishing:sturgeon_fish_golden_star
          frame: goal
          show_toast: true
          announce_chat: true
          x: 12
          y: 20
          description:
            - "这个普通节点绑定在 novice_root 下"
```
# 请注意
如果您需要基于即时事件触发的成就系统（例如：玩家挖掘方块瞬间触发、击杀实体立即检测等），那么本插件可能不适合您的需求。
Gardin's Advancement 的设计目标并不是替代事件监听系统，而是提供一个基于 PlaceholderAPI 的通用条件检测框架。



目前支持的图标适配：
- [CraftEngine](https://github.com/Xiao-MoMi/craft-engine)

## 测试环境 
1.21.11-paper