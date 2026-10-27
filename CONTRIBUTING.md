# Contributing to Watube

Thanks for wanting to help! Ideas, bug reports, design, code cleanup or deep changes are all welcome. The rules below keep Watube private, stable and easy to review.

> 🇫🇷 La politique IA et l'avis du mainteneur existent aussi [en français](#version-française).

## AI policy

AI-assisted contributions are **accepted**, under these rules:

| | |
| :--- | :--- |
| ✅ **Recommended models** | **Claude Opus 5.5** (Anthropic) · **MiMo-V2.6-Flash** (Xiaomi) — with MiMo, use [OpenCode](https://opencode.ai). |
| ⚠️ **Other models** | **Not recommended.** |
| 🧑‍💻 **Human in charge** | You read, understand and test everything the AI produced. You answer for it, not the tool. |
| 🏷️ **Disclosure** | State in the pull request whether an AI was used, and which model. |

These rules cover code, documentation, issues and pull requests alike. Documentation with the prompt I use for AI audits and advanced fixes will be added to the repository soon.

### The maintainer's view on AI

Generative AI is interesting. This is a personal opinion: some will disagree, and I understand that. I find it very useful in many situations where doing things by hand would take a very long time to get concrete results, with a much higher risk of mistakes. Picture doing the same task all day for months: it wears down your morale and, even if you love the project, the repetition may make you tired of it. That is why I allow AI for contributions.

But it must not replace the human factor, which remains essential: a human checks that audits, especially AI-assisted ones, are relevant and have not broken the project through lack of oversight.

My method: when changes pile up, for example more than 20 fixes for a major update, I delegate part of the work to the AI and keep the inspection for myself. It changes the parts I asked for, following a very precise prompt that makes it document exactly which files it touched, changed or replaced, which greatly cuts my review time. This prompt will be made available if you are interested, to make contributing easier. Minor updates, such as a bug fix, are made and inspected by hand. Audits, however, are always carried out with AI, using a prompt of more than 47,000 lines that sets out a whole procedure. Hence the importance of using the models I recommend: in my experience, they are the only ones that have proven relevant and that respect the prompts they are given.

## Before you start

- **Open an issue first** and discuss the change before you build it. Use the issue templates: issues that bypass them are closed.
- Check the [roadmap](ROADMAP.md): some features are explicitly *not planned*.

## Writing code

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep the build free of warnings.
- **Feature regions:** every piece of code sits under the name of its feature (`// region Share` … `// endregion`, `<!-- region Share -->` in XML), from the list in [FEATURES.md](FEATURES.md). Put new code in the region of its feature; a new feature gets a row in that list. The `FeatureRegionsTest` unit test checks it.
- **Privacy first:** no analytics, no telemetry, no tracking. If a change adds a network request, an identifier or third-party code (Google's above all), say so in the pull request and explain why it is needed.

## Testing

Test thoroughly, on a real device, before opening a pull request, and describe how you tested (device, Android version, steps).

## Commits and pull requests

- Branch in your fork: `git checkout -b feature/your-feature-name`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat`, `fix`, `refactor`, `ci`, `chore`), e.g. `feat: add support for 120Hz displays`.
- Open a pull request with a clear description, link its issue and fill in the template, AI disclosure included.

## Translations

Watube shares its string catalogue with the upstream LibreTube project on [Weblate](https://hosted.weblate.org/projects/libretube/#languages).

## Code of conduct

By taking part, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## Version française

### Politique IA

Les contributions assistées par IA sont **acceptées**, avec ces règles :

| | |
| :--- | :--- |
| ✅ **Modèles recommandés** | **Claude Opus 5.5** (Anthropic) · **MiMo-V2.6-Flash** (Xiaomi) — avec MiMo, utilisez [OpenCode](https://opencode.ai). |
| ⚠️ **Autres modèles** | **Non recommandés.** |
| 🧑‍💻 **L'humain décide** | Vous relisez, comprenez et testez tout ce que l'IA a produit. C'est vous qui en répondez, pas l'outil. |
| 🏷️ **Transparence** | Indiquez dans la pull request si une IA a été utilisée, et laquelle. |

Ces règles valent pour le code, la documentation, les issues et les pull requests. Une documentation avec le prompt que j'utilise pour les audits et les corrections avancées par IA sera bientôt ajoutée au dépôt.

### Mon avis sur l'IA et pourquoi je l'autorise

L'IA générative est intéressante. C'est un avis personnel : certains ne seront pas d'accord, et je le comprends. Je la trouve très utile dans beaucoup de situations où, à la main, il faudrait très longtemps pour obtenir des résultats concrets, avec bien plus de risques d'erreur. Imaginez faire la même tâche toute la journée pendant des mois : cela pèse sur le moral et, même si vous aimez le projet, la répétition risque de vous en lasser. C'est pourquoi j'autorise l'IA pour les contributions.

Mais elle ne doit pas remplacer le facteur humain, qui reste capital : c'est l'humain qui vérifie que les audits, notamment ceux faits avec l'IA, sont pertinents et n'ont pas cassé le projet faute de contrôle.

Ma méthode : quand les modifications s'accumulent, par exemple plus de 20 correctifs pour une mise à jour majeure, je délègue une partie du travail à l'IA et je garde l'inspection pour moi. Elle modifie les parties que je lui ai demandées en suivant un prompt très précis, qui l'oblige à documenter exactement les fichiers qu'elle a touchés, modifiés ou remplacés : mon temps de vérification s'en trouve fortement réduit. Ce prompt sera mis à disposition si cela vous intéresse, pour faciliter vos contributions. Les mises à jour mineures, comme une correction de bug, sont faites et inspectées à la main. Les audits, eux, sont toujours réalisés avec l'IA, à l'aide d'un prompt de plus de 47 000 lignes qui impose toute une procédure. D'où l'importance d'utiliser les modèles que je recommande : d'après mon expérience, ce sont les seuls à s'être montrés pertinents et à respecter les prompts qui leur sont confiés.

Les autres règles (issue d'abord, conventions Kotlin, zéro avertissement, confidentialité d'abord, tests sur un vrai appareil, Conventional Commits) sont détaillées en anglais plus haut.
