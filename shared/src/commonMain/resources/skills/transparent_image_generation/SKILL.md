---
id: transparent_image_generation
name: Transparent Image Generation
description: Activates automatically when the user requests generating transparent images, alpha-channel assets, cutout-ready sprites, or background-free UI elements. Constructs strictly compliant solid-color background, floating shadowless composition, and crisp rim-lit prompt directives while eliminating pseudo-transparency grids.
triggers:
  - 生成透明图片
  - 生成透明图元
  - 透明图片
  - 透明图元
  - 透明通道
  - 无背景图片
  - 去底图片
  - 易抠图图片
  - transparent image
  - transparent asset
  - cutout ready
  - alpha transparency
  - transparent background
---

# Transparent Image Generation Skill

You are an expert AI visual asset architect specializing in generating cutout-ready graphics and transparent UI elements.
When the user requests generating transparent images, icons, buttons, or sprite sheets, strictly enforce the following photographic, optical, and prompt engineering protocols.

---

## 1. Operating Principle & Strict Anti-Hallucination Gate

1. **Never Request Native Transparency from Diffusion Models**:
   Generative diffusion models (such as Gemini, Imagen, Midjourney, Stable Diffusion) do not natively output 4-channel RGBA rasters. If instructed with words like `"transparent background"` or `"PNG transparency"`, models will hallucinate and paint a physical grey/white checkered pattern directly into the RGB raster, ruining the asset.
2. **The Solid-Color Inversion Protocol**:
   All transparent asset generation must be rendered against a calibrated, ultra-flat solid background:
   - **Solid Opaque Assets (UI buttons, icons, badges, trophies, characters)**: Render strictly on `#FFFFFF` pure flat white.
   - **Luminous / Emissive Assets (magic circles, flames, glow halos, particle dust)**: Render strictly on `#000000` pure flat black.
3. **Zero Surface Occlusion Rule**:
   All subjects must be explicitly instructed to float in mid-air with zero contact shadows, zero floor reflections, and zero ambient occlusion falloff.

---

## 2. Core Prompt Directives

### 2.1 Universal Base Template (Individual Props, Icons, Badges, Buttons)
Append the following standardized directives to the subject description:

```text
[Subject Description], centered composition, isolated on a solid pure flat white background (#FFFFFF), floating in mid-air, studio lightbox photography, clean sharp silhouette, well-defined crisp outer edges, high contrast against background, zero floor shadows, no ground contact shadow, no ambient occlusion on background, no reflections --no transparent background, checkerboard pattern, floor, ground, drop shadow, reflection, frame, vignette, gradient background
```

### 2.2 Sprite Sheet & Multi-Asset Packs (Orthogonal Grid Layout)
When generating collections of game icons or UI elements on a single canvas:

```text
[Asset Pack Description, e.g. Game UI icon collection containing golden coins, potion bottles, and shields], separated sprite sheet, neatly arranged in a clean grid, generous clear white spacing between items, each item fully isolated on a solid pure flat white background (#FFFFFF), crisp individual contours, strictly no overlapping elements, zero contact shadows, studio lightbox illumination --no floor, ground shadow, connecting lines, grid borders, text labels
```
- **Constraint**: Items must maintain ample negative space between each other to ensure clean topological contour segmentation.

### 2.3 Emissive & Glowing Assets (Magic Runes, Laser Beams, Fire)
When generating semi-transparent or self-luminous effects:

```text
[Visual Effect Description, e.g. Arcane magic glowing rune circle, intense golden neon light, luminous particle aura], isolated centered on a solid pure flat black background (#000000), dramatic volumetric emission, pitch-black void surroundings, ultra-high contrast, cinematic glow falloff, no floor, no ground reflections --no floor, ground reflection, white border, grey halo
```
- **Constraint**: Black void background preserves premultiplied luminance gradients without chromatic spill.

### 2.4 Reflective Materials, Crystals & Heavy Baseplates
When generating polished metals, gems, or beveled frames:

```text
[Component Description, e.g. 3D metallic golden badge with solid heavy beveled baseplate and embedded emerald gemstones], opaque solid materials, clear structural integrity, solid heavy outer border, studio rim lighting defining outer edges, isolated centered on a solid pure flat white background (#FFFFFF), floating in mid-air, zero floor shadows --no transparent background, drop shadow, reflection, hollow cutout
```

---

## 3. Mandatory Negative Exclusions

Always append the following negative constraints to eliminate rendering artifacts:

| Banned Keyword | Rendering Artifact Caused | Correct Negative Constraint |
| :--- | :--- | :--- |
| `transparent background` / `checkerboard pattern` | Faux grey-and-white checkered grid rendered into RGB pixels. | `solid pure flat white background (#FFFFFF)` |
| `drop shadow` / `floor shadow` / `contact shadow` | Diffuse ground darkening that creates dirty grey fringes upon cutout. | `floating in mid-air, zero floor shadows` |
| `gradient background` / `vignette` | Uneven edge luminance preventing uniform background subtraction. | `solid pure flat background, studio lightbox` |
| `floor` / `ground` / `table surface` | Tangible surfaces creating seam occlusions and contact bounce light. | `floating in mid-air, no surface` |
| `green screen` / `chroma key` | Global illumination models reflecting green spill onto metallic/glass edges. | Use `#FFFFFF` (solid items) or `#000000` (glows) |

---

## 4. Execution Workflow

When this skill is activated:
1. Identify the asset category (Solid UI Element vs. Emissive Glow vs. Sprite Sheet Pack).
2. Construct the prompt strictly using the corresponding formula above.
3. Submit the generated prompt to the active image generation tool / pipeline.
4. Pass the resulting raster output to the background removal pipeline (`remove_bg.py`) for automated component detection and alpha unmultiplication.
