# リリースの手順

## 変更点の書き方

| ファイル | 言語 | 中身 |
|---|---|---|
| `CHANGELOG.md` | 英語 | **リリースノートの本文そのもの**。直近（または次）のリリース分だけを書く |
| `CHANGELOG.ja.md` | 日本語 | 全バージョン分を積み上げる |

- `CHANGELOG.md` はリリース後もそのまま残し、**次のリリースの最初の変更を書くときに丸ごと書き換える**。
- 日本語は `CHANGELOG.ja.md` の「未リリース」欄に書き足し、リリース時にその見出しをバージョン番号と日付に変える。

### 形式

```markdown
<何が変わったかの要約1文。更新前に知るべきこと（設定キーが増えたなど）があればここに書く>

### Added
- Add …

### Changed
- …

### Removed
- Remove …

### Fixed
- Fix …
```

- 見出しは `Added` → `Changed` → `Removed` → `Fixed` の順で、**項目があるものだけ**書く。変更が1〜2個なら見出しなしの箇条書きだけでよい。
- 各項目は命令形で始める（`Fix …`、`Add …`）。
- サーバーの管理者に見える変化だけを書く。
- 関係する issue や PR があればリンクを付ける。

## リリースの流れ

1. バージョンを上げる: `gradle.properties` の `version` を変える。
2. 変更点を確定する: `CHANGELOG.md` を見直し、`CHANGELOG.ja.md` の「未リリース」をバージョン見出しに変える。
3. ビルドと確認: `./gradlew build` を実行し、サーバーでの動作を確かめる。
4. 1〜3 を PR にして master にマージする。
5. タグ: master のリリース用コミットに `v<バージョン>` のタグを付けて push する。

   ```sh
   git fetch origin master
   git tag v<バージョン> origin/master
   git push origin v<バージョン>
   ```

タグを push すると `.github/workflows/release.yml` が動き、次を行う。

- `gradle.properties` の `version` がタグと一致するか確かめる（一致しなければ止まる）。
- タグのコミットをビルドする。
- `v<バージョン>` の GitHub Release を作り、mod の jar を添付する（`-sources` の jar は付けない）。タイトルはタグと同じ `v<バージョン>`、本文は `CHANGELOG.md` をそのまま使う。
