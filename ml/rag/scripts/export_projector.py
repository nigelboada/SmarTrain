from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from rag_common import CHROMA_DIR, COLLECTION_NAME, RESULTS_DIR, load_env_file


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export SmarTrain RAG embeddings for TensorFlow Projector.")
    parser.add_argument("--store", choices=["chroma", "pinecone"], default=os.environ.get("SMARTRAIN_RAG_VECTORSTORE", "chroma"))
    parser.add_argument("--chroma-dir", default=os.environ.get("SMARTRAIN_RAG_CHROMA_DIR", str(CHROMA_DIR)))
    parser.add_argument("--collection", default=os.environ.get("SMARTRAIN_RAG_COLLECTION", COLLECTION_NAME))
    parser.add_argument("--pinecone-index", default=os.environ.get("PINECONE_INDEX_NAME", "smartrain-rag"))
    parser.add_argument("--pinecone-namespace", default="smartrain")
    parser.add_argument("--limit", type=int, default=500)
    parser.add_argument("--out-dir", default=str(RESULTS_DIR / "projector"))
    return parser.parse_args()


def export_chroma(args: argparse.Namespace):
    from langchain_chroma import Chroma
    from langchain_ollama import OllamaEmbeddings

    embeddings = OllamaEmbeddings(
        model=os.environ.get("SMARTRAIN_RAG_EMBED_MODEL", "nomic-embed-text"),
        base_url=os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434"),
    )
    store = Chroma(
        persist_directory=args.chroma_dir,
        collection_name=args.collection,
        embedding_function=embeddings,
    )
    data = store._collection.get(include=["embeddings", "documents", "metadatas"])
    vectors = data.get("embeddings") or []
    documents = data.get("documents") or []
    metadatas = data.get("metadatas") or []
    return vectors[: args.limit], documents[: args.limit], metadatas[: args.limit]


def export_pinecone(args: argparse.Namespace):
    from pinecone import Pinecone

    api_key = os.environ.get("PINECONE_API_KEY", "")
    if not api_key:
        raise SystemExit("PINECONE_API_KEY is required for Pinecone export.")

    pc = Pinecone(api_key=api_key)
    index = pc.Index(args.pinecone_index)
    ids = []
    for page in index.list(namespace=args.pinecone_namespace):
        ids.extend(page)
        if len(ids) >= args.limit:
            break
    ids = ids[: args.limit]
    vectors, documents, metadatas = [], [], []
    for start in range(0, len(ids), 100):
        result = index.fetch(ids=ids[start : start + 100], namespace=args.pinecone_namespace)
        for vector_id, vector_data in result.vectors.items():
            metadata = vector_data.metadata or {}
            vectors.append(vector_data.values)
            documents.append(metadata.get("text", vector_id))
            metadatas.append(metadata)
    return vectors, documents, metadatas


def write_projector_files(vectors, documents, metadatas, out_dir: Path) -> None:
    import numpy as np
    import pandas as pd

    out_dir.mkdir(parents=True, exist_ok=True)
    vectors_matrix = np.array(vectors, dtype=float)
    if vectors_matrix.size == 0:
        raise SystemExit("No vectors found. Build the index first.")

    vectors_path = out_dir / "vectors.tsv"
    metadata_path = out_dir / "metadata.tsv"
    np.savetxt(vectors_path, vectors_matrix, delimiter="\t", fmt="%.6f")

    rows = []
    for index, (document, metadata) in enumerate(zip(documents, metadatas)):
        rows.append(
            {
                "label": str(document)[:80].replace("\t", " ").replace("\n", " ").strip(),
                "source": metadata.get("source", "unknown"),
                "category": metadata.get("category", "unknown"),
                "language": metadata.get("language", "ca"),
                "chunk_id": metadata.get("chunk_id", index),
            }
        )
    pd.DataFrame(rows).to_csv(metadata_path, sep="\t", index=False)

    preview_path = out_dir / "pca_preview.png"
    try:
        from sklearn.decomposition import PCA
        import matplotlib.cm as cm
        import matplotlib.pyplot as plt

        vectors_2d = PCA(n_components=2, random_state=42).fit_transform(vectors_matrix)
        sources = [row["source"] for row in rows]
        unique_sources = sorted(set(sources))
        color_map = {source: cm.tab10(i / max(len(unique_sources) - 1, 1)) for i, source in enumerate(unique_sources)}

        fig, ax = plt.subplots(figsize=(10, 7))
        ax.set_facecolor("#0D1117")
        fig.patch.set_facecolor("#0D1117")
        for source in unique_sources:
            indexes = [i for i, item in enumerate(sources) if item == source]
            ax.scatter(vectors_2d[indexes, 0], vectors_2d[indexes, 1], label=source, s=25, alpha=0.76, color=color_map[source])
        ax.legend(loc="best", fontsize=8, facecolor="#161B22", labelcolor="white")
        ax.set_title("SmarTrain RAG embeddings - PCA preview", color="white")
        ax.tick_params(colors="#8B949E")
        for spine in ax.spines.values():
            spine.set_edgecolor("#30363D")
        plt.tight_layout()
        plt.savefig(preview_path, dpi=150, bbox_inches="tight", facecolor="#0D1117")
        plt.close(fig)
    except ImportError:
        preview_path = None

    print(
        json.dumps(
            {
                "vectors": str(vectors_path),
                "metadata": str(metadata_path),
                "preview": str(preview_path) if preview_path else "",
                "count": len(rows),
                "dimensions": int(vectors_matrix.shape[1]),
            },
            indent=2,
        )
    )


def main() -> None:
    load_env_file()
    args = parse_args()
    if args.store == "chroma":
        vectors, documents, metadatas = export_chroma(args)
    else:
        vectors, documents, metadatas = export_pinecone(args)
    write_projector_files(vectors, documents, metadatas, Path(args.out_dir))


if __name__ == "__main__":
    main()
