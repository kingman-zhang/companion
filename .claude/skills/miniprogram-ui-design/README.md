# Mini Program UI Design Skill

微信小程序 UI 设计与重构规范，团队可复用。

## 内容

| 文件 | 作用 |
|---|---|
| `SKILL.md` | 主规范文档，Claude Code 自动读取 |
| `references/design-tokens.wxss` | CSS 变量 token，可直接引入项目 |
| `references/checklist.md` | 重构 / PR 时打勾用的 checklist |
| `references/anti-patterns.md` | 15 个常见反模式与修复方法 |
| `references/component-patterns.wxml` | 标准卡片/按钮/Toast 等 WXML 范式 |

## 在 Claude Code 中使用

### 方式一：项目级（推荐，团队共享）

把整个 `miniprogram-ui-design/` 目录放到项目根目录的 `.claude/skills/` 下：

```
your-project/
├── .claude/
│   └── skills/
│       └── miniprogram-ui-design/
│           ├── SKILL.md
│           └── references/
├── pages/
└── ...
```

Claude Code 会在每次涉及 `.wxml`/`.wxss` 文件时自动加载此 skill。团队成员只要 clone 项目就拥有同一份规范。

### 方式二：用户级（个人用，多项目共享）

放到 `~/.claude/skills/miniprogram-ui-design/`。所有项目都能用，但团队成员不会自动同步。

### 方式三：当作纯文档用

不用 skill 机制也可以——直接把 `SKILL.md` 改名为 `DESIGN_GUIDELINES.md` 放项目根目录，PR 时让团队成员对照阅读。

## 在项目中接入 design tokens

1. 复制 `references/design-tokens.wxss` 到你项目的 `styles/tokens.wxss`
2. 修改文件顶部的 `--md-color-primary` 等品牌色
3. 在 `app.wxss` 顶部添加：
   ```css
   @import "styles/tokens.wxss";
   ```
4. 在页面 wxss 中使用 CSS 变量：
   ```css
   .my-card {
     background: var(--md-bg-card);
     padding: var(--md-space-4);
     border-radius: var(--md-radius-md);
     box-shadow: var(--md-shadow-sm);
   }
   ```

## 与 Claude Code 协作的推荐工作流

1. 把要重构的 `.wxml` / `.wxss` 文件以及当前截图发给 Claude
2. 让它对照 `checklist.md` 逐项打分
3. 让它从 `anti-patterns.md` 中找出当前页面命中的反模式
4. 让它用 `component-patterns.wxml` 的范式重写代码
5. 用 `design-tokens.wxss` 替换所有硬编码颜色和数值

例如可以这样跟 Claude Code 说：

> 参考 miniprogram-ui-design skill，对 pages/home/index.wxml 做重构。先按 checklist 打分，列出命中的 anti-patterns，再给出重构后的代码。所有颜色/间距/字号用 design-tokens 里的 CSS 变量。

## 参考来源

本规范综合自：

- 微信官方设计指南：https://developers.weixin.qq.com/miniprogram/design/
- WeUI 设计规范：https://weui.io/
- 优设：参考近 100 款案例后的小程序设计指南、小程序尺寸规范、小程序设计精简版指南
- 团队实战经验沉淀

## 维护

发现规范有不一致或需要更新时：

1. 修改 `SKILL.md` 或对应 `references/*.md`
2. 在 commit message 中写明来源（如"根据 XX 案例补充反模式 #16"）
3. 团队 review 通过后合入

---

最后更新：2026-05
