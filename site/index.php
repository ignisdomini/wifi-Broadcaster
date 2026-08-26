<?php
declare(strict_types=1);

/**
 * Главная РАДИОИНФОРМАТОРА.
 *
 * Страница объясняет не технологию, а повод: кафе, рынок, тропа, беда. Всё
 * содержимое лежит в настройках и правится через админку — в шаблоне нет ни
 * одной зашитой надписи, кроме подписей к каналам, которые приходят из
 * lib/channels.php.
 *
 * Общей ленты здесь нет: показывать чужие объявления всем подряд значило бы
 * ровно то, от чего радиоинформатор уходит — шум для тех, кто далеко.
 */

require_once __DIR__ . '/lib/bootstrap.php';
efir_require_installed();

$siteName = (string)setting('site_name');

// Ввод кода — это не поиск по людям: код нельзя угадать, его выдаёт эфир.
$codeError = null;
$code = trim((string)($_GET['code'] ?? ''));
if ($code !== '') {
    if (!is_valid_code($code) || find_user_by_profile_code($code) === null) {
        $codeError = (string)setting('gate_error');
    } else {
        header('Location: /u/' . $code);
        exit;
    }
}

$cases = [
    ['🍲', (string)setting('case1_title'), (string)setting('case1_text')],
    ['％',  (string)setting('case2_title'), (string)setting('case2_text')],
    ['🧺', (string)setting('case3_title'), (string)setting('case3_text')],
    ['⛰',  (string)setting('case4_title'), (string)setting('case4_text')],
    ['⚠',  (string)setting('case5_title'), (string)setting('case5_text')],
    ['📋', (string)setting('case6_title'), (string)setting('case6_text')],
    ['💬', (string)setting('case7_title'), (string)setting('case7_text')],
    ['👥', (string)setting('case8_title'), (string)setting('case8_text')],
];

$steps = [
    [(string)setting('step1_title'), (string)setting('step1_text')],
    [(string)setting('step2_title'), (string)setting('step2_text')],
    [(string)setting('step3_title'), (string)setting('step3_text')],
];

$dmCards = [
    [(string)setting('dm1_title'), (string)setting('dm1_text')],
    [(string)setting('dm2_title'), (string)setting('dm2_text')],
    [(string)setting('dm3_title'), (string)setting('dm3_text')],
];

$ribbon = array_filter([
    (string)setting('ribbon_1'),
    (string)setting('ribbon_2'),
    (string)setting('ribbon_3'),
    (string)setting('ribbon_4'),
], static fn(string $item): bool => trim($item) !== '');
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?= e($siteName) ?> — <?= e((string)setting('tagline')) ?></title>
    <meta name="description" content="<?= e((string)setting('intro')) ?>">
    <meta name="theme-color" content="#E8590C">
    <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body>

<header class="top">
    <div class="top-inner">
        <a class="brand" href="/">
            <span class="brand-dot"></span>
            <span class="brand-mark"><?= e($siteName) ?></span>
            <span class="brand-tag"><?= e((string)setting('tagline')) ?></span>
        </a>
        <nav class="top-nav">
            <a href="#komu"><?= e((string)setting('cases_label')) ?></a>
            <a href="#kak"><?= e((string)setting('how_label')) ?></a>
            <a href="#kanaly"><?= e((string)setting('channels_label')) ?></a>
            <a href="#lenta"><?= e((string)setting('gate_label')) ?></a>
        </nav>
    </div>
</header>

<main>
    <section class="hero">
        <div>
            <?php if (trim((string)setting('hero_eyebrow')) !== ''): ?>
                <span class="hero-eyebrow">
                    <span class="brand-dot"></span>
                    <?= e((string)setting('hero_eyebrow')) ?>
                </span>
            <?php endif; ?>

            <h1 class="hero-title">
                <?= e((string)setting('hero_title_1')) ?><br>
                <span class="glow"><?= e((string)setting('hero_title_2')) ?></span>
            </h1>

            <p class="hero-lead"><?= e((string)setting('intro')) ?></p>

            <div class="hero-actions">
                <?php
                // Магазин — главный путь установки: там обновления приходят сами.
                // Прямой APK остаётся запасным, для тех, у кого магазина нет.
                $store = trim((string)setting('rustore_url'));
                $apk = apk_download();
                ?>
                <?php if ($store !== ''): ?>
                    <?= store_badge($store) ?>
                <?php endif; ?>

                <?php if ($apk !== null): ?>
                    <a class="btn <?= $store !== '' ? 'btn-ghost' : '' ?>" href="<?= e($apk['url']) ?>" download>
                        <?= e((string)setting('hero_btn_apk')) ?>
                        <span class="btn-sub"><?= e($apk['size']) ?></span>
                    </a>
                <?php endif; ?>

                <a class="btn btn-ghost" href="#kak"><?= e((string)setting('hero_btn_primary')) ?></a>
                <a class="btn btn-ghost" href="#lenta"><?= e((string)setting('hero_btn_secondary')) ?></a>
            </div>
        </div>

        <!-- Рупор с расходящимися волнами и парой перехваченных объявлений -->
        <div class="radar" aria-hidden="true">
            <span class="radar-ring"></span>
            <span class="radar-ring"></span>
            <span class="radar-ring"></span>
            <span class="radar-ring"></span>
            <span class="radar-core">📣</span>

            <div class="radar-bubble"><b>Кафе «Пар» · к2</b>Обед за 250 до 15:00</div>
            <div class="radar-bubble"><b>Рынок · к4</b>Привезли черешню, 3 ряд</div>
            <div class="radar-bubble"><b>Тропа · к5</b>Стоянка у брода занята</div>
        </div>
    </section>

    <?php if ($ribbon): ?>
        <div class="ribbon">
            <div class="ribbon-inner">
                <?php foreach ($ribbon as $item): ?>
                    <span><?= e($item) ?></span>
                <?php endforeach; ?>
            </div>
        </div>
    <?php endif; ?>

    <section class="section" id="komu">
        <div class="section-head">
            <p class="section-label"><?= e((string)setting('cases_label')) ?></p>
            <h2 class="section-title"><?= e((string)setting('cases_title')) ?></h2>
            <p class="section-text"><?= e((string)setting('cases_text')) ?></p>
        </div>

        <div class="cases">
            <?php foreach ($cases as [$icon, $title, $text]): ?>
                <?php if (trim($title) === '') continue; ?>
                <article class="case">
                    <div class="case-icon"><?= e($icon) ?></div>
                    <h3><?= e($title) ?></h3>
                    <p><?= e($text) ?></p>
                </article>
            <?php endforeach; ?>
        </div>
    </section>

    <div class="section-alt">
        <section class="section" id="kak">
            <div class="section-head">
                <p class="section-label"><?= e((string)setting('how_label')) ?></p>
                <h2 class="section-title"><?= e((string)setting('how_title')) ?></h2>
                <p class="section-text"><?= e((string)setting('how_text')) ?></p>
            </div>

            <div class="steps">
                <?php foreach ($steps as [$title, $text]): ?>
                    <?php if (trim($title) === '') continue; ?>
                    <div class="step">
                        <h3><?= e($title) ?></h3>
                        <p><?= e($text) ?></p>
                    </div>
                <?php endforeach; ?>
            </div>
        </section>
    </div>

    <section class="section" id="kanaly">
        <div class="section-head">
            <p class="section-label"><?= e((string)setting('channels_label')) ?></p>
            <h2 class="section-title"><?= e((string)setting('channels_title')) ?></h2>
            <p class="section-text"><?= e((string)setting('channels_text')) ?></p>
        </div>

        <div class="channels">
            <?php foreach (all_channels() as $channel): ?>
                <?php
                // Первый и тревожный выделяются: один — вход по умолчанию,
                // второй должен бросаться в глаза, когда до него дойдёт дело.
                $extra = match ((int)$channel['channel']) {
                    EFIR_CHANNEL_DEFAULT => ' channel-first',
                    6 => ' channel-alarm',
                    default => '',
                };
                ?>
                <div class="channel<?= $extra ?>">
                    <span class="channel-num"><?= (int)$channel['channel'] ?></span>
                    <span>
                        <b><?= e($channel['title']) ?></b>
                        <span><?= e($channel['hint']) ?></span>
                    </span>
                </div>
            <?php endforeach; ?>
        </div>
    </section>

    <div class="section-alt">
        <section class="section" id="lichnoe">
            <div class="section-head">
                <p class="section-label"><?= e((string)setting('dm_label')) ?></p>
                <h2 class="section-title"><?= e((string)setting('dm_title')) ?></h2>
                <p class="section-text"><?= e((string)setting('dm_text')) ?></p>
            </div>

            <div class="cases">
                <?php foreach ($dmCards as [$title, $text]): ?>
                    <?php if (trim($title) === '') continue; ?>
                    <article class="case">
                        <h3><?= e($title) ?></h3>
                        <p><?= e($text) ?></p>
                    </article>
                <?php endforeach; ?>
            </div>
        </section>
    </div>

    <section class="gate" id="lenta">
        <div class="gate-inner">
            <p class="section-label"><?= e((string)setting('gate_label')) ?></p>
            <h2 class="section-title"><?= e((string)setting('gate_title')) ?></h2>
            <p class="section-text"><?= e((string)setting('gate_text')) ?></p>

            <form class="gate-form" method="get" action="/">
                <input type="text" name="code" maxlength="24" required
                       placeholder="<?= e((string)setting('gate_placeholder')) ?>"
                       value="<?= e($codeError !== null ? $code : '') ?>"
                       autocomplete="off" spellcheck="false">
                <button class="btn" type="submit"><?= e((string)setting('gate_button')) ?></button>
            </form>

            <?php if ($codeError !== null): ?>
                <p style="margin-top:1rem;color:#FFD8C2"><?= e($codeError) ?></p>
            <?php endif; ?>

            <?php if ($store !== ''): ?>
                <!-- Второй раз кнопка стоит здесь намеренно: до низа страницы
                     дочитывает тот, кто уже решился, и возвращать его наверх
                     за ссылкой невежливо. -->
                <p style="margin-top:2rem"><?= store_badge($store) ?></p>
            <?php endif; ?>
        </div>
    </section>
</main>

<footer class="bottom">
    <div class="bottom-inner">
        <span><?= e($siteName) ?> · <?= e((string)setting('tagline')) ?></span>
        <span>
            <?= e((string)setting('footer_note')) ?>
            &nbsp;·&nbsp;<a href="/privacy.php">Конфиденциальность</a>
        </span>
    </div>
</footer>

</body>
</html>
