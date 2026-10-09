import matplotlib.pyplot as plt
import numpy as np
import os

# -------------------------------------------------------------
# Global academic figure settings
# -------------------------------------------------------------
plt.rcParams.update({
    'font.family': 'sans-serif',
    'font.sans-serif': ['DejaVu Sans', 'Arial', 'Helvetica'],
    'font.size': 7.5,
    'axes.labelsize': 8.0,
    'axes.titlesize': 8.5,
    'xtick.labelsize': 7.0,
    'ytick.labelsize': 7.0,
    'figure.dpi': 300,
})

output_dir = r"c:\Users\wasib\OneDrive\Desktop\java project\figures"
os.makedirs(output_dir, exist_ok=True)

# =============================================================
# Chart 1: Retrieval Performance Comparison
# =============================================================
models = ['BM25', 'TF-IDF', 'S-BERT', 'BGE-M3', 'Hybrid']

mrr = [0.738, 0.775, 0.871, 0.903, 0.924]
p5 = [0.840, 0.880, 0.960, 1.000, 1.000]
ndcg10 = [0.789, 0.818, 0.905, 0.928, 0.946]

x = np.arange(len(models))

# FIX: narrower bars + a bigger gap between bars in a group so the
# value labels no longer collide with each other or the next bar.
# (old: width=0.19, offset=0.21 -> only ~0.02 gap between bars)
width = 0.16
offset = 0.195

# FIX: slightly larger canvas so there's room to breathe (old: 2.70 x 1.85)
fig, ax = plt.subplots(figsize=(3.15, 1.95))

# Academic palette
c_mrr = '#3A6B88'
c_p5 = '#E07A5F'
c_ndcg = '#6A9955'

r1 = ax.bar(
    x - offset, mrr, width,
    label='MRR',
    color=c_mrr,
    edgecolor='#222222',
    linewidth=0.5,
    zorder=3
)

r2 = ax.bar(
    x, p5, width,
    label='P@5',
    color=c_p5,
    edgecolor='#222222',
    linewidth=0.5,
    zorder=3
)

r3 = ax.bar(
    x + offset, ndcg10, width,
    label='NDCG@10',
    color=c_ndcg,
    edgecolor='#222222',
    linewidth=0.5,
    zorder=3
)

# -------------------------------------------------------------
# Value labels
# -------------------------------------------------------------
def add_value_labels(container):
    labels = []

    for bar in container:
        h = bar.get_height()

        # Force consistent formatting
        labels.append(f'{h:.2f}')

    ax.bar_label(
        container,
        labels=labels,
        padding=2.0,
        fontsize=5.5,
        color='#222222',
        rotation=90         # FIX 2: rotate labels vertically. Horizontal text was
                             # wide enough to spill sideways into a taller neighboring
                             # bar (e.g. a 0.91 label next to a 0.96 bar) even with the
                             # wider gap — vertical text has almost no horizontal
                             # footprint, so it can't overlap the bar beside it.
    )

add_value_labels(r1)
add_value_labels(r2)
add_value_labels(r3)

# -------------------------------------------------------------
# Axes
# -------------------------------------------------------------
ax.set_ylabel('Score', labelpad=2, fontweight='medium')

ax.set_xticks(x)
ax.set_xticklabels(models, fontweight='medium')

# FIX: a touch more headroom above 1.00 so the "1.00" labels clear the legend
ax.set_ylim(0.65, 1.14)
ax.set_yticks([0.70, 0.80, 0.90, 1.00])

# -------------------------------------------------------------
# Grid and spines
# -------------------------------------------------------------
ax.yaxis.grid(
    True,
    linestyle='--',
    alpha=0.45,
    color='#cccccc',
    zorder=0,
    linewidth=0.5
)

ax.set_axisbelow(True)

ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)

ax.spines['left'].set_color('#555555')
ax.spines['bottom'].set_color('#555555')

ax.spines['left'].set_linewidth(0.6)
ax.spines['bottom'].set_linewidth(0.6)

# -------------------------------------------------------------
# Legend
# -------------------------------------------------------------
ax.legend(
    loc='upper left',
    ncol=3,
    frameon=True,
    framealpha=0.95,
    edgecolor='#d0d0d0',
    fancybox=True,
    handlelength=0.9,
    handletextpad=0.25,
    columnspacing=0.5,
    borderpad=0.25,
    labelspacing=0.2
)

# -------------------------------------------------------------
# Layout / export
# -------------------------------------------------------------
fig.tight_layout(pad=0.25)

fig.savefig(
    os.path.join(output_dir, 'fig_retrieval.pdf'),
    bbox_inches='tight'
)

fig.savefig(
    os.path.join(output_dir, 'fig_retrieval.png'),
    dpi=300,
    bbox_inches='tight'
)

plt.close(fig)


# =============================================================
# Chart 2: Gap Discovery Precision
# =============================================================
modules = ['Topic', 'Temporal', 'Meth.', 'Interdisc.']
precision = [0.80, 0.85, 0.75, 0.70]

x_gap = np.arange(len(modules))
width_gap = 0.44

fig, ax = plt.subplots(figsize=(2.15, 1.80))

c_gap = '#3A6B88'

bars = ax.bar(
    x_gap,
    precision,
    width_gap,
    color=c_gap,
    edgecolor='#222222',
    linewidth=0.5,
    zorder=3
)

# Clean value labels
ax.bar_label(
    bars,
    labels=[f'{v:.2f}' for v in precision],
    padding=2,
    fontsize=6.5,
    fontweight='medium',
    color='#111111'
)

ax.set_ylabel('Precision@5', labelpad=2, fontweight='medium')

ax.set_xticks(x_gap)
ax.set_xticklabels(modules, fontweight='medium')

ax.set_ylim(0.0, 1.05)
ax.set_yticks([0.0, 0.25, 0.50, 0.75, 1.00])

ax.yaxis.grid(
    True,
    linestyle='--',
    alpha=0.45,
    color='#cccccc',
    zorder=0,
    linewidth=0.5
)

ax.set_axisbelow(True)

ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)

ax.spines['left'].set_color('#555555')
ax.spines['bottom'].set_color('#555555')

ax.spines['left'].set_linewidth(0.6)
ax.spines['bottom'].set_linewidth(0.6)

fig.tight_layout(pad=0.25)

fig.savefig(
    os.path.join(output_dir, 'fig_gap.pdf'),
    bbox_inches='tight'
)

fig.savefig(
    os.path.join(output_dir, 'fig_gap.png'),
    dpi=300,
    bbox_inches='tight'
)

plt.close(fig)

print("Corrected retrieval and gap charts successfully generated.")