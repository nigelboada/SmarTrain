from __future__ import annotations

import argparse
import json
import os
import shutil
import time
from pathlib import Path
from uuid import uuid4

from rag_common import CHROMA_DIR, COLLECTION_NAME, DATA_PATH, load_corpus, load_env_file


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the SmarTrain RAG vector index.")
    parser.add_argument("--store", choices=["chroma", "pinecone"], default=os.environ.get("SMARTRAIN_RAG_VECTORSTORE", "chroma"))
    parser.add_argument("--embedding-provider", choices=["ollama", "sentence-transformers"], default="ollama")
    parser.add_argument("--embedding-model", default="", help="Default: nomic-embed-text for Ollama, all-MiniLM-L6-v2 for sentence-transformers.")
    parser.add_argument(
        "--ollama-base-url",
        default=os.environ.get("SMARTRAIN_RAG_EMBED_BASE_URL", os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434")),
    )
    parser.add_argument("--chunk-size", type=int, default=700)
    parser.add_argument("--chunk-overlap", type=int, default=90)
    parser.add_argument("--chroma-dir", default=os.environ.get("SMARTRAIN_RAG_CHROMA_DIR", str(CHROMA_DIR)))
    parser.add_argument("--collection", default=os.environ.get("SMARTRAIN_RAG_COLLECTION", COLLECTION_NAME))
    parser.add_argument("--reset", action="store_true", help="Delete the local Chroma directory before rebuilding.")
    parser.add_argument("--pinecone-index", default=os.environ.get("PINECONE_INDEX_NAME", "smartrain-rag"))
    parser.add_argument("--pinecone-namespace", default="smartrain")
    parser.add_argument("--pinecone-cloud", default=os.environ.get("PINECONE_CLOUD", "aws"))
    parser.add_argument("--pinecone-region", default=os.environ.get("PINECONE_REGION", "us-east-1"))
    return parser.parse_args()


def build_documents(chunk_size: int, chunk_overlap: int):
    from langchain_core.documents import Document
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    raw_docs = [
        Document(page_content=item["text"], metadata=item["metadata"])
        for item in load_corpus()
        if item["text"].strip()
    ]
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        length_function=len,
        separators=["\n\n", "\n", ". ", " ", ""],
    )
    chunks = splitter.split_documents(raw_docs)
    for index, chunk in enumerate(chunks):
        source = chunk.metadata.get("source", "unknown")
        chunk.metadata["chunk_id"] = index
        chunk.metadata["id"] = f"{source}:{index}"
        chunk.metadata.setdefault("language", "ca")
    return raw_docs, chunks


def build_embeddings(provider: str, model: str, ollama_base_url: str):
    if provider == "ollama":
        from langchain_ollama import OllamaEmbeddings

        return OllamaEmbeddings(
            model=model or "nomic-embed-text",
            base_url=ollama_base_url,
        )

    from langchain_huggingface import HuggingFaceEmbeddings

    return HuggingFaceEmbeddings(
        model_name=model or "sentence-transformers/all-MiniLM-L6-v2",
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )


def build_chroma(args: argparse.Namespace, chunks, embeddings) -> None:
    from langchain_chroma import Chroma

    chroma_dir = Path(args.chroma_dir)
    if args.reset and chroma_dir.exists():
        shutil.rmtree(chroma_dir)
    chroma_dir.mkdir(parents=True, exist_ok=True)

    vectorstore = Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        collection_name=args.collection,
        persist_directory=str(chroma_dir),
        collection_metadata={"hnsw:space": "cosine"},
    )
    print(json.dumps({"store": "chroma", "collection": args.collection, "vectors": vectorstore._collection.count(), "path": str(chroma_dir)}, indent=2))


def build_pinecone(args: argparse.Namespace, chunks, embeddings) -> None:
    from langchain_pinecone import PineconeVectorStore
    from pinecone import Pinecone, ServerlessSpec

    api_key = os.environ.get("PINECONE_API_KEY", "")
    if not api_key:
        raise SystemExit("PINECONE_API_KEY is required for --store pinecone. Put it in .env or the environment, never in source code.")

    dimension = len(embeddings.embed_query("smartrain dimension test"))
    pc = Pinecone(api_key=api_key)
    existing = [item.name for item in pc.list_indexes()]
    if args.pinecone_index not in existing:
        pc.create_index(
            name=args.pinecone_index,
            dimension=dimension,
            metric="cosine",
            spec=ServerlessSpec(cloud=args.pinecone_cloud, region=args.pinecone_region),
        )
        while not pc.describe_index(args.pinecone_index).status["ready"]:
            time.sleep(2)

    index = pc.Index(args.pinecone_index)
    vectorstore = PineconeVectorStore(index=index, embedding=embeddings, namespace=args.pinecone_namespace)
    ids = [f"smartrain-{chunk.metadata.get('chunk_id', i)}-{uuid4()}" for i, chunk in enumerate(chunks)]
    vectorstore.add_documents(chunks, ids=ids)
    time.sleep(3)
    print(json.dumps({"store": "pinecone", "index": args.pinecone_index, "namespace": args.pinecone_namespace, "uploaded": len(ids)}, indent=2))


def main() -> None:
    load_env_file()
    args = parse_args()
    if not DATA_PATH.exists():
        raise SystemExit(f"Missing RAG knowledge base: {DATA_PATH}")

    raw_docs, chunks = build_documents(args.chunk_size, args.chunk_overlap)
    embeddings = build_embeddings(args.embedding_provider, args.embedding_model, args.ollama_base_url)
    test_dimension = len(embeddings.embed_query("SmarTrain RAG"))

    print(json.dumps({"raw_documents": len(raw_docs), "chunks": len(chunks), "embedding_dimension": test_dimension}, indent=2))
    if args.store == "chroma":
        build_chroma(args, chunks, embeddings)
    else:
        build_pinecone(args, chunks, embeddings)


if __name__ == "__main__":
    main()
