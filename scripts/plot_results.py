import csv
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULT = os.path.join(ROOT, "results")
OUT = os.path.join(RESULT, "grafik")

SURFACE = "#fcfcfb"
INK = "#0b0b0b"
INK2 = "#52514e"
GRID = "#e4e3df"
COLOR = {"GoCJ": "#2a78d6", "Sintetik": "#eb6834"}

METRICS = [
    ("makespan_s", "Makespan", "detik", "makespan"),
    ("total_cost_gd", "Total Cost", "G$", "total_cost"),
    ("utilization_pct", "Resource Utilization", "%", "utilization"),
    ("imbalance", "Degree of Imbalance", "", "imbalance"),
    ("throughput_tps", "Throughput", "task/detik", "throughput"),
    ("scheduling_ms_mean", "Convergence Speed (waktu penjadwalan)", "ms", "convergence"),
]

plt.rcParams.update({
    "font.size": 10,
    "axes.edgecolor": GRID,
    "axes.labelcolor": INK2,
    "xtick.color": INK2,
    "ytick.color": INK2,
    "text.color": INK,
    "figure.facecolor": SURFACE,
    "axes.facecolor": SURFACE,
})


def read(path):
    with open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def fmt(v):
    if abs(v) >= 1000:
        return f"{v:,.0f}".replace(",", ".")
    if abs(v) >= 10:
        return f"{v:.1f}".replace(".", ",")
    return f"{v:.3f}".replace(".", ",")


def axis_fmt(top):
    d = 0 if top >= 10 else (2 if top >= 1 else 3)
    def f(v, _):
        t = f"{v:,.{d}f}"
        return t.replace(",", "X").replace(".", ",").replace("X", ".")
    return matplotlib.ticker.FuncFormatter(f)


def style(ax):
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)
    ax.tick_params(length=0)
    ax.yaxis.set_major_formatter(axis_fmt(ax.get_ylim()[1]))


def combined_chart(rows, key, title, unit, slug):
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.2))
    for ax, source in zip(axes, ("GoCJ", "Sintetik")):
        pts = sorted((int(r["tasks"]), float(r[key])) for r in rows if r["source"] == source)
        xs, ys = zip(*pts)
        name = "GoCJ" if source == "GoCJ" else "Sintetis"
        ax.plot(xs, ys, color=COLOR[source], linewidth=2, marker="o", markersize=5,
                markeredgecolor=SURFACE, markeredgewidth=1.5)
        for i in (0, len(xs) - 1):
            ax.annotate(fmt(ys[i]), (xs[i], ys[i]), textcoords="offset points",
                        xytext=(0, 8), ha="center", fontsize=9, color=INK2)
        ax.set_title(f"Dataset {name}", loc="left", fontsize=11, fontweight="bold", color=INK)
        ax.set_xlabel("Jumlah task")
        ax.set_ylabel(f"{title.split(' (')[0]}" + (f" ({unit})" if unit else ""))
        ax.set_xticks(xs)
        if len(xs) > 12:
            ax.set_xticklabels([str(x) if i % 2 == 0 else "" for i, x in enumerate(xs)])
        if key == "utilization_pct":
            ax.set_ylim(0, 105)
        else:
            ax.set_ylim(0, max(ys) * 1.12)
        style(ax)
    fig.suptitle(f"{title} (LPT)", x=0.01, ha="left", fontsize=12, fontweight="bold", color=INK)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, f"{slug}_gabungan.png"), dpi=150)
    plt.close(fig)


def class_chart(stats):
    want = [("GoCJ_Dataset_1000", "GoCJ 1000 task", "GoCJ",
             ["Pendek\n(<= 100.000 MI)", "Sedang\n(100.001-350.000 MI)", "Panjang\n(> 350.000 MI)"]),
            ("Synthetic_1000", "Sintetis 1000 task", "Sintetik",
             ["Kecil\n(1.000-10.000 MI)", "Sedang\n(10.001-30.000 MI)", "Besar\n(30.001-50.000 MI)"])]
    fig, axes = plt.subplots(1, 2, figsize=(10, 4.2), sharey=True)
    for ax, (ds, label, src, cats) in zip(axes, want):
        r = next(x for x in stats if x["dataset"] == ds)
        vals = [float(r["pct_short"]), float(r["pct_medium"]), float(r["pct_long"])]
        bars = ax.bar(cats, vals, color=COLOR[src], width=0.55)
        for b, v in zip(bars, vals):
            ax.annotate(f"{v:.1f}%".replace(".", ","), (b.get_x() + b.get_width() / 2, v),
                        textcoords="offset points", xytext=(0, 4), ha="center",
                        fontsize=9, color=INK2)
        ax.set_title(label, loc="left", fontsize=11, fontweight="bold", color=INK)
        ax.set_ylim(0, 75)
        style(ax)
        ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: f"{v:.0f}%"))
        ax.set_ylim(0, 75)
    axes[0].set_ylabel("Persentase task")
    fig.suptitle("Karakteristik panjang task per kategori", x=0.01, ha="left",
                 fontsize=12, fontweight="bold", color=INK)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, "karakteristik_dataset_1000.png"), dpi=150)
    plt.close(fig)


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = read(os.path.join(RESULT, "hasil_lpt.csv"))
    stats = read(os.path.join(RESULT, "dataset_stats.csv"))
    for key, title, unit, slug in METRICS:
        combined_chart(rows, key, title, unit, slug)
    class_chart(stats)
    print("Grafik tersimpan di", OUT)
    for f in sorted(os.listdir(OUT)):
        print(" ", f)


if __name__ == "__main__":
    sys.exit(main())