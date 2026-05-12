#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Render classic Chen-style ER diagram (rectangles + rhombuses) to PNG."""
from __future__ import annotations

import math
from pathlib import Path


def main() -> None:
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError as e:
        raise SystemExit("请先在 venv 中安装 Pillow: pip install Pillow") from e

    root = Path(__file__).resolve().parents[2]
    out_png = root / "docs" / "ER图_老式Chen风格.png"

    W, H = 1600, 1100
    bg = "#ffffff"
    entity_fill = "#dae8fc"
    entity_stroke = "#6c8ebf"
    rh_fill = "#fff2cc"
    rh_stroke = "#d6b656"
    line_color = "#333333"
    text_main = "#000000"
    text_card = "#444444"

    img = Image.new("RGB", (W, H), bg)
    draw = ImageDraw.Draw(img)

    def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
        candidates = [
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/Library/Fonts/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        ]
        for p in candidates:
            pp = Path(p)
            if pp.exists():
                try:
                    return ImageFont.truetype(str(pp), size=size, index=0)
                except OSError:
                    continue
        return ImageFont.load_default()

    font_title = load_font(18)
    font_body = load_font(14)
    font_small = load_font(12)
    font_card = load_font(13)

    def rounded_rect(xy: tuple[int, int, int, int], fill: str, outline: str, radius: int = 6) -> None:
        draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=2)

    def diamond(cx: int, cy: int, rw: int, rh: int) -> tuple[float, ...]:
        pts = [
            (cx, cy - rh),
            (cx + rw, cy),
            (cx, cy + rh),
            (cx - rw, cy),
        ]
        flat: list[float] = []
        for x, y in pts:
            flat.extend((float(x), float(y)))
        return tuple(flat)

    def draw_diamond(cx: int, cy: int, rw: int, rh: int, label: str) -> None:
        pts = diamond(cx, cy, rw, rh)
        draw.polygon(pts, fill=rh_fill, outline=rh_stroke)
        tw, th = draw.textbbox((0, 0), label, font=font_body)[2:]
        draw.text((cx - tw // 2, cy - th // 2), label, fill=text_main, font=font_body)

    def draw_entity(x: int, y: int, w: int, h: int, title: str, lines: list[str]) -> None:
        rounded_rect((x, y, x + w, y + h), entity_fill, entity_stroke)
        tx, ty = x + w // 2, y + 14
        tw, th = draw.textbbox((0, 0), title, font=font_title)[2:]
        draw.text((tx - tw // 2, ty), title, fill=text_main, font=font_title)
        ly = y + 44
        for ln in lines:
            lw, lh = draw.textbbox((0, 0), ln, font=font_small)[2:]
            draw.text((x + w // 2 - lw // 2, ly), ln, fill=text_main, font=font_small)
            ly += lh + 2

    def arrow_line(
        x1: float,
        y1: float,
        x2: float,
        y2: float,
        card_near_start: str | None,
        card_near_end: str | None,
    ) -> None:
        draw.line([(x1, y1), (x2, y2)], fill=line_color, width=2)
        ang = math.atan2(y2 - y1, x2 - x1)
        ah = 12
        aw = 7
        bx, by = x2, y2
        p1 = (bx - ah * math.cos(ang) + aw * math.sin(ang), by - ah * math.sin(ang) - aw * math.cos(ang))
        p2 = (bx - ah * math.cos(ang) - aw * math.sin(ang), by - ah * math.sin(ang) + aw * math.cos(ang))
        draw.polygon([p1, p2, (bx, by)], fill=line_color)

        def place_card(label: str, ux: float, uy: float, vx: float, vy: float) -> None:
            mx, my = (ux + vx) / 2, (uy + vy) / 2
            dx, dy = vx - ux, vy - uy
            ln = math.hypot(dx, dy) or 1.0
            nx, ny = -dy / ln * 18, dx / ln * 18
            lw, lh = draw.textbbox((0, 0), label, font=font_card)[2:]
            draw.text((mx - lw // 2 + nx, my - lh // 2 + ny), label, fill=text_card, font=font_card)

        if card_near_start:
            place_card(card_near_start, x2, y2, x1, y1)
        if card_near_end:
            place_card(card_near_end, x1, y1, x2, y2)

    # Entity boxes (x, y, w, h) — Chinese names per project schema（与 draw.io 版对齐）
    user = dict(x=620, y=56, w=220, h=108, cx=730, cy=110, bx=730, by=164)
    meme = dict(x=68, y=328, w=230, h=118, cx=183, cy=387, bx=298, by=446)
    gen = dict(x=558, y=458, w=344, h=132, cx=730, cy=524, bx=730, by=590)
    plaza = dict(x=1078, y=328, w=260, h=118, cx=1208, cy=387, bx=1208, by=446)
    article = dict(x=1078, y=628, w=260, h=118, cx=1208, cy=687, bx=1208, by=746)

    draw_entity(
        user["x"],
        user["y"],
        user["w"],
        user["h"],
        "用户信息表（users）",
        ["用户ID（PK）· 账号· 密码哈希· openid· 昵称· 状态"],
    )
    draw_entity(
        meme["x"],
        meme["y"],
        meme["w"],
        meme["h"],
        "表情包主数据表（meme_assets）",
        ["表情包ID（PK）· 文件URL· 向量ID· 场景· 公开"],
    )
    draw_entity(
        gen["x"],
        gen["y"],
        gen["w"],
        gen["h"],
        "用户生成图片记录表（user_generated_images）",
        ["生成记录ID（PK）· 用户ID（FK）· 素材ID（FK可空）· 结果图URL"],
    )
    draw_entity(
        plaza["x"],
        plaza["y"],
        plaza["w"],
        plaza["h"],
        "公共广场内容表（plaza_contents）",
        ["广场内容ID（PK）· 类型· 关联素材（FK可空）· 创建人（FK可空）"],
    )
    draw_entity(
        article["x"],
        article["y"],
        article["w"],
        article["h"],
        "公共广场文章详情表（plaza_articles）",
        ["详情ID（PK）· 广场内容ID（FK唯一）· 正文· 阅读/点赞"],
    )

    # Diamonds（中心 cx,cy；半宽 rw；半高 rh）与 docs/ER图_老式Chen风格.drawio 一致
    r_own = dict(cx=730, cy=294, rw=92, rh=58, tx=730, ty=236)
    r_src = dict(cx=448, cy=536, rw=92, rh=58, tx=448, ty=478)
    r_create = dict(cx=968, cy=178, rw=92, rh=58, tx=968, ty=120)
    r_ref = dict(cx=760, cy=386, rw=92, rh=58, tx=760, ty=328)
    r_article = dict(cx=1208, cy=566, rw=92, rh=58, tx=1208, ty=508)

    draw_diamond(r_own["cx"], r_own["cy"], r_own["rw"], r_own["rh"], "拥有生成")
    draw_diamond(r_src["cx"], r_src["cy"], r_src["rw"], r_src["rh"], "素材来源")
    draw_diamond(r_create["cx"], r_create["cy"], r_create["rw"], r_create["rh"], "创建广场")
    draw_diamond(r_ref["cx"], r_ref["cy"], r_ref["rw"], r_ref["rh"], "引用素材")
    draw_diamond(r_article["cx"], r_article["cy"], r_article["rw"], r_article["rh"], "文章详情")

    # Links: entity border to diamond — approximate anchor points
    arrow_line(user["bx"], user["by"], r_own["cx"], r_own["cy"] - r_own["rh"], "1", None)
    arrow_line(r_own["cx"], r_own["cy"] + r_own["rh"], gen["cx"], gen["y"], None, "N")

    arrow_line(meme["bx"], meme["cy"], r_src["cx"] - r_src["rw"], r_src["cy"], "1", None)
    arrow_line(r_src["cx"] + r_src["rw"], r_src["cy"], gen["x"], gen["cy"], None, "N")

    arrow_line(user["x"] + user["w"], user["cy"], r_create["cx"] - r_create["rw"], r_create["cy"], "1", None)
    arrow_line(r_create["cx"] + r_create["rw"], r_create["cy"], plaza["x"], plaza["cy"], None, "N")

    arrow_line(meme["bx"], meme["cy"], r_ref["cx"] - r_ref["rw"], r_ref["cy"], "1", None)
    arrow_line(r_ref["cx"] + r_ref["rw"], r_ref["cy"], plaza["x"], plaza["cy"], None, "N")

    arrow_line(plaza["cx"], plaza["by"], r_article["cx"], r_article["cy"] - r_article["rh"], "1", None)
    arrow_line(r_article["cx"], r_article["cy"] + r_article["rh"], article["cx"], article["y"], None, "1")

    note = (
        "说明：连线旁标注基数；素材来源、引用素材、创建人为可选参与（字段可空）。"
        "Milvus 向量库仅通过 embedding_id 逻辑关联，未在图中单独建实体。"
    )
    draw.text((40, H - 54), note, fill="#555555", font=font_small)

    title = "Smart Meter Condition — 数据库 ER 图（Chen：矩形·菱形）"
    tw, _ = draw.textbbox((0, 0), title, font=font_title)[2:]
    draw.text(((W - tw) // 2, 12), title, fill="#111111", font=font_title)

    out_png.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_png, format="PNG", dpi=(150, 150))
    print(f"Wrote {out_png}")


if __name__ == "__main__":
    main()
