# 手動動作確認手順

サンドボックス IDE 上で Repo Lens の縦のワークフローを目視確認するための手順。
`samples/` は既定の閾値のままで 6 つの Check がすべて検出されるように作られている。

## 1. サンドボックスの起動

```bash
cd ~/IdeaProjects/repo-lens
export JAVA_HOME="$HOME/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew runIde
```

- 普段の IDE とは別プロセス・別設定の使い捨て IDEA が起動する。
- **コードを変更したらサンドボックスを閉じて起動し直す。** プラグインの
  ホットリロードは無効化してある（unload-safe でないため）。
- サンドボックス起動中は `buildSearchableOptions` を含むビルドが
  「Only one instance of IDEA can be run at a time」で失敗する。ビルドは閉じてから。

起動したら `~/IdeaProjects/repo-lens` 自体をプロジェクトとして開く。

Go の構造解析を確認する場合は、サンドボックス内で一度だけ JetBrains Account に
ログインし（Ultimate が適用される）、Settings → Plugins から Go を入れて再起動する。
ログイン・プラグインとも `.intellijPlatform/sandbox` の設定に保持されるため、
サンドボックスを立ち上げ直しても再設定は不要。

## 2. サンプルでの検出確認（Selected Files スコープ）

**前提: 閾値が既定値であること。** 設定はプロジェクト単位で永続化されるため、
過去の確認で変更した閾値（例: class 50 / method 10）が残っていると下表と一致しない。
Settings → Tools → Repo Lens で file 800 / class 500 / method 80 / params 7 /
nesting 5 に戻してから実行する。

Project View で `samples` ディレクトリを右クリック → **Analyze with Repo Lens**。

Project スコープでも検出はされるが、`docs/` の設計書自体が「TODO」という語を
大量に含むため一覧がノイズだらけになる。per-check の確認は Selected Files で行う。

### 期待される Findings

| Check | File | Symbol | Value / Threshold |
|---|---|---|---|
| Large File | `samples/large_file_sample.txt` | *(空欄)* | 851 / 800 |
| Large Class | `samples/java/LargeClassSample.java` | `LargeClassSample` | 627 / 500 |
| Large Method | `samples/java/LargeMethodSample.java` | `LargeMethodSample.oversized()` | ≈92 / 80 |
| Too Many Parameters | `samples/java/WideParametersSample.java` | `WideParametersSample.configure()` | 9 / 7 |
| Too Many Parameters | `samples/kotlin/KotlinSamples.kt` | `KotlinSamples.configure()` | 9 / 7 |
| Deep Nesting | `samples/java/DeepNestingSample.java` | `DeepNestingSample.deep()` | 6 / 5 |
| Deep Nesting | `samples/kotlin/KotlinSamples.kt` | `KotlinSamples.deep()` | 6 / 5 |
| TODO / FIXME | `samples/todo_markers_sample.md` | *(空欄)* | *(空欄)* ×2 |
| TODO / FIXME | `samples/kotlin/KotlinSamples.kt` | *(空欄)* | *(空欄)* ×2 |

**Go プラグインがある場合のみ**（Settings → Plugins で Go をインストール。Ultimate ライセンスが必要）:

| Check | File | Symbol | Value / Threshold |
|---|---|---|---|
| Too Many Parameters | `samples/go/go_samples.go` | `Configure()` | 9 / 7 |
| Deep Nesting | `samples/go/go_samples.go` | `Deep()` | 6 / 5 |
| TODO / FIXME | `samples/go/go_samples.go` | *(空欄)* | *(空欄)* ×1 |

Go プラグインが無い場合は上記 3 行のうち TODO の 1 件だけが出る（構造解析は
黙ってスキップされる）。これ自体が optional dependency の確認になる。
`Narrow()`（2 パラメータ）は Go プラグインの有無に関わらず出ない。

**JavaScript プラグインがある場合**（Ultimate では同梱・既定で有効）:

| Check | File | Symbol | Value / Threshold |
|---|---|---|---|
| Too Many Parameters | `samples/ts/ts_samples.ts` | `configure()` | 9 / 7 |
| Deep Nesting | `samples/ts/ts_samples.ts` | `deep()` | 6 / 5 |
| TODO / FIXME | `samples/ts/ts_samples.ts` | *(空欄)* | *(空欄)* ×1 |

`narrow()`（2 パラメータ）は出ない。

出てはいけないもの（境界値の確認）:

- `WideParametersSample.narrow()`（3 パラメータ）
- `DeepNestingSample.atLimit()`（深さちょうど 5）
- `todo_markers_sample.md` の `METHODOLOGY` / `TODOS`（単語境界で不一致）

Symbol 列は構造系 Check（Large Class / Method / Parameters / Nesting）にだけ
名前が入り、Tier 0（Large File / TODO）では空欄のままが正しい。

## 3. 操作の確認

1. **Single click** — 行を選ぶと下段の Detail が更新される
2. **Double click / Enter** — 対象ファイルの宣言行（TODO は該当行）へジャンプ
3. **フィルタ** — Search に `nesting`、Severity を `Warning`、Check を
   `Deep Nesting` にすると絞り込まれ、ステータスが `Showing N of M` になる
4. **Copy（3種）** — 複数行を Cmd+クリックで選択して各ボタンを押し、貼り付けて確認:
   - **Copy** — 1 Finding = 1 行のパイプ区切りテキスト。コード断片なし
   - **Copy with Code** — 上記 + 行番号付きコード断片。上限超過時は
     `... omitted N lines ...`
   - **Copy for AI** — Markdown。`# Repo Lens Review Context` ヘッダ、
     File / Symbol / Location / Value / Threshold / Reason、フェンス付き断片、
     末尾に review 依頼文

## 4. スコープの確認

| スコープ | 手順 | 期待結果 |
|---|---|---|
| Current File | `KotlinSamples.kt` を開いて Analyze | このファイルの 4 件のみ |
| Module | 同上のまま Scope を Module に | モジュール全体（Project とほぼ同じ） |
| Project | Scope を Project に | samples + docs の TODO 等も含む |
| Local Changes | 何かを編集して Analyze | 編集したファイルのみ。変更なしなら `No local changes to analyze` |

Local Changes の対象は **Git の前回コミットとの差分**（IntelliJ の VCS 統合が
把握している変更 + 未追跡ファイル）。エディタの開閉や IDE セッションとは無関係で、
コミット / revert すれば対象から外れる。stash・shelve 中の変更と削除済みファイルは
含まれない。VCS 未設定のプロジェクトではスコープ自体が利用不可と表示される。
| Current File（未オープン） | 全エディタを閉じて Analyze | `No file is open in the editor` |

## 5. Settings の確認

Settings → Tools → Repo Lens:

1. **Checks の有効/無効** — `TODO / FIXME (RL-T001)` のチェックを外して
   再 Analyze → TODO の Findings が消える。チェックを戻すと復活する
2. **閾値** — Large method threshold を 80 → 3 に変更して再 Analyze →
   サンプル中の小さいメソッドも検出される。戻すと消える
3. **Exclusions** — `**/samples/**` を 1 行追加して再 Analyze →
   samples の Findings が消える（明示選択した場合を除く）。行を削除して戻す
4. **Language Capabilities** — Settings → Tools → Repo Lens の先頭に
   Provider 一覧が表示される。Java / Kotlin、Go、JavaScript / TypeScript が
   すべて ✓ であること（Go プラグインを無効化して IDE を再起動すると
   Go だけ「—」+ 理由表示になる。これが Provider unavailable の正常表示）
5. **永続化** — サンドボックスを閉じて再起動しても設定が残っている
   （プロジェクトの `.idea/repoLens.xml` に保存される）

## 6. トラブルシューティング

- サンドボックスのログ: `.intellijPlatform/sandbox/plugin/IU-2026.1.5/log/idea.log`
- 解析の診断ログ: 同ログに `RepoLensAnalysisService - analysis scope=... RL-F001=12ms ...`
  の形式で Analyzer 単位の所要時間が出る（ソース本文は出力しない）
- 解析が返らない場合はステータスラベルとログの ERROR / SEVERE を確認
- 表示が古い場合はサンドボックスの再起動（ビルドの反映には再起動が必要）
