<?php
declare(strict_types=1);

/**
 * Отдельная запись: то, что ушло в эфир, и прикреплённый к ней текст.
 * Ссылки на автора здесь нет — к ленте ведёт только код, пойманный из воздуха.
 *
 * Счётчик просмотров считается, но наружу не показывается: он ничего не даёт
 * читателю и превращает запись в предмет соревнования. Число видит только
 * администратор.
 */

require_once __DIR__ . '/lib/bootstrap.php';
efir_require_installed();

$code = (string)($_GET['c'] ?? '');
$post = null;

if ($code !== '' && is_valid_code($code)) {
    $post = find_post_by_code($code);
}

$siteName = (string)setting('site_name');

if ($post === null) {
    http_response_code(404);
    ?>
    <!doctype html>
    <html lang="ru">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Запись не найдена — <?= e($siteName) ?></title>
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
        </div>
    </header>
    <div class="shell">
        <div class="notice notice-error">
            <p>Такой записи нет.</p>
            <p class="muted">
                Код <code><?= e(mb_substr($code, 0, 16)) ?></code> не найден: он мог быть
                удалён, истечь по сроку хранения или просто быть набран с ошибкой.
            </p>
            <p><a href="/">На главную</a></p>
        </div>
    </div>
    </body>
    </html>
    <?php
    exit;
}

increment_views((int)$post['id']);
$view = post_public_view($post);
$title = $post['broadcast'] !== '' ? (string)$post['broadcast'] : $view['excerpt'];
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?= e(mb_substr($title, 0, 70)) ?> — <?= e($siteName) ?></title>
    <meta property="og:title" content="<?= e(mb_substr($title, 0, 70)) ?>">
    <meta property="og:description" content="<?= e($view['excerpt']) ?>">
    <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body>

<header class="top">
    <div class="top-inner">
        <a class="brand" href="/">
            <span class="brand-mark"><?= e($siteName) ?></span>
            <span class="brand-tag"><?= e((string)setting('tagline')) ?></span>
        </a>
        <nav class="top-nav"><a href="/">На главную</a></nav>
    </div>
</header>

<main class="shell">
    <article class="post">
        <div class="post-meta">
            <span><?= e((string)$post['nick'] !== '' ? (string)$post['nick'] : 'аноним') ?></span>
            <span><?= e(human_time((string)$post['created_at'])) ?></span>
            <span class="chip">к<?= (int)$view['channel'] ?> · <?= e(channel_title((int)$view['channel'])) ?></span>
        </div>

        <?php if ($post['broadcast'] !== ''): ?>
            <p class="post-broadcast">
                <span class="post-broadcast-label">В эфир ушло</span>
                <?= e((string)$post['broadcast']) ?>
            </p>
        <?php endif; ?>

        <?php if (trim((string)$post['body']) !== ''): ?>
            <?php /* переносы держит white-space: pre-wrap, поэтому без nl2br */ ?>
            <div class="post-body"><?= e((string)$post['body']) ?></div>
        <?php endif; ?>

        <div class="post-foot"><?= e(post_url((string)$post['code'])) ?></div>
    </article>
</main>

<footer class="bottom">
    <div class="bottom-inner">
        <span><?= e($siteName) ?> · <?= e((string)setting('tagline')) ?></span>
        <span>Эта страница была прикреплена к передаче</span>
    </div>
</footer>

</body>
</html>
