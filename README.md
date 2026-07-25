# Gardin's Advancement
您不想写自己的数据包：那对于您来说太过复杂，所以您想要一个插件，通过更友善的yml实现将自定义成就添加进你的服务器。
但一直以来我们的成就都依赖于插件开发者制作的事件监听器，这实际上对用户产生了一些限制：
譬如使用当您使用CraftEngine时，除非作者编写了对应的适配，不然他的自定义成就系统永远无法知道用户拿到的是一张纸还是一个食物。
现如今placeholder几乎充斥在所有的插件里，越来越多的插件选择它作为暴露内部状态的工具
所以使用它作为条件再好不过了，既能保持成就插件本身轻量，又能天然兼容所有提供 Placeholder 的插件。

Gardin's Advancement 正是基于这一理念设计的。

插件本身不依赖大量事件监听器，而是使用 Placeholder 表达式作为统一的条件系统。只要某个插件提供了 Placeholder，就可以直接参与成就判定，无需等待额外的适配。

例如：
```yml
- "placeholder: %player_level% >= 15 && %townyadvanced_player_jailed% == true"
```
上面的配置表示：

玩家等级达到 15 级或以上；
玩家当前在 Towny 中处于监禁状态。

只有两个条件同时满足，玩家才会获得对应的成就。

由于采用了这种设计，理论上任何支持 PlaceholderAPI 的插件都可以直接成为成就条件来源

一个示例文件如下
```yml
tabs:
  novice_path:
    background: "minecraft:textures/block/stone.png"
    advancements:
      novice_root:
        type: root
        data:
          title: "初入世界"
          icon: CE:namespace:PAPER
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
          title: "&b迈出第一步"
          icon: CE:customfishing:sturgeon_fish_golden_star
          frame: goal
          show_toast: true
          announce_chat: true
          x: 12
          y: 20
          description:
            - "&7这个普通节点绑定在 novice_root 下"

      explorer_root:
        type: root
        data:
          title: "探索开始"
          icon: CE:namespace:PAPER
          frame: task
          show_toast: true
          announce_chat: true
          x: 10
          y: 10
          description:
            - "这是第二条线路的根节点"

      explorer_step_one:
        type: common
        parent: explorer_root
        conditions:
          - "placeholder: %player_level% >= 10"
        data:
          title: "&b探索者 I"
          icon: CE:namespace:PAPER
          frame: goal
          show_toast: true
          announce_chat: true
          x: 12
          y: 18
          description:
            - "这个普通节点绑定在 explorer_root 下"
      explorer_step_two:
        type: common
        parent: explorer_root
        conditions:
          - "placeholder: %player_level% >= 15 && %player_food_level% <= 17"
        data:
          title: "探索者 II"
          icon: CE:namespace:PAPER
          frame: challenge
          show_toast: true
          announce_chat: true
          x: 12
          y: 22
          description:
            - "这个普通节点也绑定在 explorer_root 下"
```

# 当前待解决的问题
颜色代码的支持还不起作用
