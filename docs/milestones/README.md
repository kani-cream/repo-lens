# Repo Lens Milestones

このディレクトリは、`docs/design.md` のリリース計画を実装可能な単位へ分解したマイルストーン定義を管理する。

## Release roadmap

| Version | Theme | Primary outcome |
|---|---|---|
| [v0.1](./v0.1.md) | Core workflow | Analyze → Findings → Navigate → Copy for AI の縦のワークフローを完成させる |
| [v0.2](./v0.2.md) | Structural analysis | Unused Candidate / Circular Dependency / ignore・suppress を追加する |
| [v0.3](./v0.3.md) | Git-aware review | Branch Diff / Large Diff / Git history / Hotspot を追加する |
| [v0.4](./v0.4.md) | Language expansion | JavaScript / TypeScript を中心に Language Analyzer Provider を拡張する |
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

## マイルストーン運用

各バージョンは、文書内の Acceptance Criteria をすべて満たした時点で完了とする。次バージョンの機能を先行実装してもよいが、未完了の必須条件を後続機能で覆い隠さない。

仕様変更が発生した場合は、まず `docs/design.md` と対象マイルストーンの整合性を確認する。大きな方針変更は設計書を更新し、実装上の具体化のみであればマイルストーン側を更新する。
