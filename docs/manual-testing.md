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
| Large Type | `samples/java/LargeClassSample.java` | `LargeClassSample` | 627 / 500 |
| Large Function / Method | `samples/java/LargeMethodSample.java` | `LargeMethodSample.oversized()` | ≈92 / 80 |
| Too Many Parameters | `samples/java/WideParametersSample.java` | `WideParametersSample.configure()` | 9 / 7 |
| Too Many Parameters | `samples/kotlin/KotlinSamples.kt` | `KotlinSamples.configure()` | 9 / 7 |
| Deep Nesting | `samples/java/DeepNestingSample.java` | `DeepNestingSample.deep()` | 6 / 5 |
| Deep Nesting | `samples/kotlin/KotlinSamples.kt` | `KotlinSamples.deep()` | 6 / 5 |
| TODO / FIXME | `samples/todo_markers_sample.md` | *(空欄)* | *(空欄)* ×2 |
| TODO / FIXME | `samples/kotlin/KotlinSamples.kt` | *(空欄)* | *(空欄)* ×2 |

| Circular Dependency | `samples/java/cycle/alpha/Alpha.java` | *(空欄)* | 2 |
| Unused Candidate | `samples/java/unused/OrphanSample.java` | `OrphanSample` / `OrphanSample.neverCalled()` | *(空欄)* |

Circular Dependency は Detail に `Cycle edges:`（各辺の file:line）、Copy for AI に
`### Dependency cycle` セクションが出る。ダブルクリックで import 行へ飛ぶ。

Unused Candidate の注意点:

- **samples の public 宣言はほぼすべて候補として出る**（サンプルはどこからも
  参照されないため）。表に無い Unused Candidate 行が複数出るのは正常
- 循環サンプルの `Alpha` / `Beta` は互いに参照し合っているため**出ない**
  （参照検索が機能している証拠）
- Detail に `Confidence: Low` と、Reflection / DI 等を検出できない旨の
  限界説明が出る。Copy for AI にも `- Confidence: Low` が入る
- Indexing 中に Analyze すると、この Check だけがスキップされ、ステータスに
  `1 check(s) skipped`（ツールチップに理由）が出る

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

Symbol 列は構造系 Check（Large Type / Method / Parameters / Nesting）にだけ
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

## 6. Branch Diff の確認

feature branch 上のプロジェクトで行う（`repo-lens` 自体を開発中 branch で
開くのが手軽。main と HEAD が同一だと差分ゼロで Total 0 になる）。

1. Scope を **Branch Diff** → Analyze → base（自動検出なら origin/main 等）
   との差分ファイルだけが対象になる。Local Changes と違い、**コミット済みの
   branch 変更も含む**
2. 差分が大きいファイルがあれば **Large Diff**（`RL-G001`）が
   `Value = 追加+削除行数` で出る。message に `+added −deleted` と status
   （added / modified / renamed）
3. Large Diff の行を Copy for AI → `- Diff: +A −D (status)` 行が入る
4. Settings → Branch Diff → Base branch に存在しない名前（例: `nope`）を
   入れて Analyze → `Base branch 'nope' does not exist...` がステータスに出る
   （空欄に戻すと自動検出に復帰）
5. 閾値: Large diff threshold（既定 300 changed lines）は Thresholds 群で変更可
6. **Untracked** — `git add` していない新規ファイルを作って Analyze →
   Branch Diff に `+N −0 (added)` で含まれる（大きければ Large Diff も発火）。
   `git diff` には現れないファイルだが、レビュー対象として扱うのが仕様
7. **履歴キャッシュ** — 同一 HEAD で 2 回 Analyze すると、ログに
   `history cache hit` が出て `enrich=` が下がる（残りは TODO ファイルへの
   blame 分）

## 7. Git history / Long-lived TODO の確認

Git 管理下のプロジェクトで任意の Scope を Analyze する。

1. **Git evidence** — 任意の Finding の Detail 下部に
   `Git: N commit(s) by M author(s) in the last 90 days, last modified X day(s) ago`
   が出る（履歴が window 内に無いファイルでは出ない）。Copy for AI にも
   `- Git: ...` 行が入る
2. **Marker age** — TODO / FIXME の Finding に `Marker age: N day(s)` が出る。
   N ≥ 90（Settings → Git → Long-lived TODO age で変更可）なら `(long-lived)`
   が付き、Reason 末尾に "This marker has been in place for N days." が入る
3. **劣化動作** — Git の無いプロジェクト（または Git プラグイン無効）では
   これらの行が出ないだけで、Finding 自体は従来どおり
4. **性能** — 履歴クエリは Analyze 1回につき 1 プロセス（`git log --since`）。
   診断ログの解析時間が大きく伸びていないこと

## 8. Hotspot の確認

直近に活発なコミットのあるプロジェクト（開発中の repo-lens 自体が好例）で
Project を Analyze する。

1. **Hotspot**（`RL-H001`、Warning）が出る: 直近 window 内のコミットが
   Hotspot minimum commits（既定 3）以上あり、かつ構造系 Finding
   （Large Type / Function / Parameters / Nesting / Circular）を持つファイル。
   Value = コミット数 × 構造 Finding 数
2. **説明可能性** — Reason にコミット数・author 数・構成 Check 名・計算式が
   すべて書かれている。Copy for AI にもそのまま入る
3. **Sort** — 一覧のカラムヘッダをクリックすると並べ替えできる
   （Check でソートすると Hotspot がまとまる）
4. Tier 0 だけのファイル（TODO のみ等）は Hotspot にならない

## 9. Ignore / Suppress の確認

1. **Ignore** — 一覧の行を右クリック → **Ignore Finding** → 行が消え、
   ステータスに `N hidden (N ignored)` が出る。ルール抑制分は
   `M by rules` として別に数えられる
2. **Show hidden** — フィルタ行のチェックを入れると ignored の Finding が
   再表示され、Detail 先頭に `[Ignored by you]` と出る。右クリック →
   **Stop Ignoring** で通常表示に戻る
3. **Suppress rule** — Settings → Repo Lens → Suppression に
   `RL-M001 | **/*_test.go` を追加 → Go テストの Large Function / Method が
   一覧から消える（Show hidden で確認可能。Detail は `[Hidden by a suppress rule]`）
4. **永続化** — サンドボックス再起動後も ignore / rule が残っている
5. **再解析安定性** — 再 Analyze しても ignored の Finding は ignored のまま
   （stable ID によるため。対象行が編集で移動した場合は新 ID になり再表示される）

## 10. 日本語 UI の確認

サンドボックスの Settings → Appearance & Behavior → System Settings →
Language and Region（または Region & Language）で言語を **日本語** にして再起動。

1. Tool Window のボタン（解析 / 停止 / コピー / コード付きコピー / AI 向けコピー）、
   フィルタ（重大度 / チェック / 非表示分を表示）、カラムヘッダが日本語になる
2. Scope コンボが「プロジェクト / 現在のファイル / … / ブランチ差分」になる
3. ステータス（合計 N / 非表示 N 件…）とエラー（Base branch が存在しない等）が日本語
4. Settings → Tools → Repo Lens の全ラベルが日本語
5. **Finding の Reason 文と Copy 出力は英語のまま**（成果物は言語設定に依存しない、
   という設計。docs/checks.md 参照）
6. 言語を English に戻すと全て英語に戻る

## 11. トラブルシューティング

- サンドボックスのログ: `.intellijPlatform/sandbox/plugin/IU-2026.1.5/log/idea.log`
- 解析の診断ログ: 同ログに `RepoLensAnalysisService - analysis scope=... RL-F001=12ms ...`
  の形式で Analyzer 単位の所要時間が出る（ソース本文は出力しない）
- 解析が返らない場合はステータスラベルとログの ERROR / SEVERE を確認
- 表示が古い場合はサンドボックスの再起動（ビルドの反映には再起動が必要）
