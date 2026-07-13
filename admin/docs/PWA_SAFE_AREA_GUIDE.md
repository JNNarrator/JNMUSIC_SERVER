# iOS PWA 安全区域适配经验总结

## 问题现象

PWA 添加到 iPhone 主屏幕后，以 `standalone` 模式启动时：

- 底部播放栏不在屏幕最底部，下方有黑色空白区域
- 歌词/播放器全屏页面顶部与灵动岛/状态栏重叠
- 底部系统 Home Indicator 遮挡应用内容

## 根因分析

### 1. CSS 高度单位在 iOS PWA 下不可靠

| 单位 | 在 iOS PWA standalone 下的行为 |
|------|-------------------------------|
| `100vh` | 等于 Safari 可视区域高度（不含浏览器 UI），但不等于物理屏幕高度 |
| `100dvh` | 动态视口高度，PWA 下理论应等于屏幕高度，实际 iOS 17 下有偏差 |
| `-webkit-fill-available` | 有效但不稳定 |
| `window.innerHeight` | JS 获取的值可能也不等于物理高度 |

**结论：不要依赖任何高度计算来让容器占满屏幕。**

### 2. 正确方案：`position: fixed; inset: 0`

`position: fixed` 相对于**布局视口**定位。配合 `<meta viewport-fit=cover>`，布局视口延伸到物理屏幕边缘。`inset: 0` 即占满整个物理屏幕。

```css
.app-shell {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}
```

### 3. 底部栏的孤岛间隙 (Island Gap)

iOS 底部存在一个约 62px 的不可布局区域，但 `env(safe-area-inset-bottom)` 只返回 34px。导致 padding 不足以覆盖到物理底部。

**修复：用 ::after 伪元素 + background: inherit 扩展背景色到物理底部。**

```css
.bottom-bar {
  flex-shrink: 0;
  position: relative;
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 34px));
  box-sizing: border-box;
}

.bottom-bar::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: calc(env(safe-area-inset-bottom, 34px) + 20px);
  background: inherit;
  transform: translateY(100%);
  pointer-events: none;
}
```

### 4. JS 回退方案

当 `env()` 不生效时，通过 `screen.height - window.innerHeight` 计算差值：

```js
const fixBottomBar = () => {
  const diff = window.screen.height - window.innerHeight
  if (diff > 0) {
    document.documentElement.style.setProperty('--safe-bottom', diff + 'px')
  }
}
window.addEventListener('load', fixBottomBar)
window.addEventListener('resize', fixBottomBar)
```

### 5. 顶部安全区域（灵动岛/状态栏）

顶部使用 `env(safe-area-inset-top)` 作为 padding-top，确保内容不被灵动岛遮挡：

```css
.app-shell {
  padding-top: calc(16px + env(safe-area-inset-top, 0px));
}
```

## 最终布局结构

```
html, body { height: 100%; overflow: hidden; }
body { height: 100dvh; }

.shell {
  position: fixed; inset: 0;
  display: flex; flex-direction: column;
  box-sizing: border-box;
  padding-top: calc(16px + env(safe-area-inset-top, 0px));
}

header { flex-shrink: 0 }

.stage { flex: 1; overflow-y: auto }

.player-bar {
  flex-shrink: 0;
  position: relative;
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 34px));
}

.player-bar::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: calc(env(safe-area-inset-bottom, 34px) + 20px);
  background: inherit;
  transform: translateY(100%);
  pointer-events: none;
}
```

## 前置条件

`index.html` 必须包含：

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
```

`manifest.json` 必须设置：

```json
{ "display": "standalone" }
```

## 测试要点

- 必须使用真机测试，Safari 开发者工具模拟器无法准确还原底部安全区域
- 添加到主屏幕后从桌面图标打开，不可在 Safari 浏览器内测试
- 如果更换了应用的 URL 路径，需要删除旧的主屏幕图标后重新添加

## 收获总结

1. **在 iOS PWA 下，CSS 高度相关的一切都不可信。** 能不用 height 就不用，用 position: fixed 替代。
2. **flex 布局 + flex-shrink: 0 实现底部定位**，比 position: absolute 更可靠。
3. **::after 伪元素是填充安全区间隙的利器**，background: inherit 自动匹配背景色。
4. **env(safe-area-inset-*) 始终带 fallback**（34px 是 iPhone 全面屏的典型值）。
5. **一次只改一处，构建后真机验证。** PWA 问题无法在桌面浏览器复现，每次改动都必须部署到真机测试。
