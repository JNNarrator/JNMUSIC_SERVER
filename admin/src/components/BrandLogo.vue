<script setup lang="ts">
// 可爱的黑胶唱片 + 微笑标签 logo，尺寸随父容器 font-size 通过 currentColor 与 em 缩放。
defineProps<{ size?: number }>()
</script>

<template>
  <svg
    class="brand-logo"
    :width="size || 34"
    :height="size || 34"
    viewBox="0 0 64 64"
    role="img"
    aria-label="JNMusic"
  >
    <defs>
      <radialGradient :id="`jn-disc-${(size || 34)}`" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="#2a1e1a" />
        <stop offset="100%" stop-color="#0f0a10" />
      </radialGradient>
      <linearGradient :id="`jn-label-${(size || 34)}`" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="var(--jn-accent)" />
        <stop offset="100%" stop-color="var(--jn-accent-strong)" />
      </linearGradient>
    </defs>
    <circle cx="32" cy="32" r="30" :fill="`url(#jn-disc-${(size || 34)})`" />
    <g fill="none" stroke="rgba(246,236,220,0.16)" stroke-width="0.8">
      <circle cx="32" cy="32" r="26" />
      <circle cx="32" cy="32" r="23" />
      <circle cx="32" cy="32" r="20" />
      <circle cx="32" cy="32" r="17" />
    </g>
    <circle cx="32" cy="32" r="12" :fill="`url(#jn-label-${(size || 34)})`" />
    <g fill="var(--jn-accent-ink)">
      <circle cx="28.5" cy="30" r="1.5" />
      <circle cx="35.5" cy="30" r="1.5" />
    </g>
    <path
      d="M27.5 34.2 Q32 38.4 36.5 34.2"
      fill="none"
      stroke="var(--jn-accent-ink)"
      stroke-width="1.5"
      stroke-linecap="round"
    />
    <circle cx="32" cy="32" r="1.6" fill="#100c11" />
    <g class="sparkle">
      <circle cx="49" cy="17" r="2.2" fill="var(--jn-accent)" />
      <circle cx="53.5" cy="12.5" r="1.2" fill="var(--jn-accent-strong)" />
    </g>
  </svg>
</template>

<style scoped>
.brand-logo {
  display: block;
  filter: drop-shadow(0 6px 14px var(--jn-glow));
  transition: transform 0.6s ease;
}
.brand-logo:hover { transform: rotate(20deg); }
.sparkle { animation: twinkle 2.2s ease-in-out infinite; transform-origin: 50px 15px; }
@keyframes twinkle {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.55; transform: scale(0.85); }
}
</style>
