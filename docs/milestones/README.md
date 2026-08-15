# Repo Lens Milestones

このディレクトリは、`docs/design.md` のリリース計画を実装可能な単位へ分解したマイルストーン定義を管理する。

## Release roadmap

| Version | Theme | Primary outcome |
|---|---|---|
| [v0.1](./v0.1.md) | Core workflow | Analyze → Findings → Navigate → Copy for AI の縦のワークフローを完成させる |
| [v0.2](./v0.2.md) | Structural analysis | Unused Candidate / Circular Dependency / ignore・suppress を追加する |
| [v0.3](./v0.3.md) | Git-aware review | Branch Diff / Large Diff / Git history / Hotspot を追加する |
| [v0.4](./v0.4.md) | Language Providers | Go を第一優先に、JavaScript / TypeScript へ Language Analyzer Provider を拡張する |
| [v1.0](./v1.0.md) | Stable release | 安定性・互換性・ドキュメントを整え、Marketplace 公開可否を判断する |

## 共通原則

すべてのマイルストーンで以下を維持する。

- Repo Lens 自身は AI API を呼び出さない。
- API key / token を保持しない。
- ソースコードを自動で外部送信しない。
- 重い解析を EDT 上で実行しない。
- Finding は断定ではなくレビュー候補として扱う。
- IntelliJ Platform の Public API を優先する。
- 既存 IntelliJ 機能を再実装せず、レビュー操作レイヤーとして統合する。
- Repo Lens 本体には単一の「対応言語」制限を設けず、Analyzer ごとに利用可能な capability を判定する。
- Language Provider がない言語でも、Large File / TODO-FIXME / Git 系など言語非依存 Analyzer は可能な範囲で利用できる。

## 実施順序の変更（2026-08-15）

v0.1 完了時点の判断として、**v0.4 の Go Provider を v0.2 / v0.3 より先に実施する**。

理由:

- 実際に利用する Repository が Go / TypeScript 中心であり、構造解析4種が
  Java / Kotlin 限定のままでは v0.1 の Exit review（実運用評価）が成立しない。
- v0.1 で `CodeStructureProvider` 拡張ポイントと optional dependency 構造を
  先行導入済みであり、Provider SPI の言語非依存性の検証（v0.4 の本来の目的）を
  今行うことが、v0.2 以降の Analyzer 設計にも先に制約を与えられる。

本ロードマップの表は本来の計画として維持し、進行状況は各マイルストーン文書の
完了記録で管理する。この前倒しは README の「次バージョンの機能を先行実装して
もよい」の範囲内であり、v0.2 / v0.3 の必須条件を覆い隠すものではない。

**結果（2026-08-15）**: v0.4 は前倒しのまま完了した（PR #9〜#11）。
次の実装対象は本来の順序に戻り v0.2 となる。

## 製品バージョンとマイルストーンの関係

**製品バージョン（plugin version）はリリース通番であり、本ディレクトリの
vX.Y はテーマ別マイルストーン名である。両者は 1:1 に対応しない**（前倒し
実施により対応関係が崩れたため、明示的に分離する）。

| Plugin version | 内容 | 対応マイルストーン |
|---|---|---|
| 0.1.0 | Core review workflow | v0.1 |
| 0.2.0 | Language Providers（Go / JS / TS）+ capability 表示 | v0.4 |

以後もリリースごとに minor を上げ、Marketplace 公開判断（v1.0 マイルストーン）
の時点で 1.0.0 とする。

## マイルストーン運用

各バージョンは、文書内の Acceptance Criteria をすべて満たした時点で完了とする。次バージョンの機能を先行実装してもよいが、未完了の必須条件を後続機能で覆い隠さない。

仕様変更が発生した場合は、まず `docs/design.md` と対象マイルストーンの整合性を確認する。大きな方針変更は設計書を更新し、実装上の具体化のみであればマイルストーン側を更新する。
