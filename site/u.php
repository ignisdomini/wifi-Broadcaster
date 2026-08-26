<?php
declare(strict_types=1);

/**
 * Личная лента. Адрес — случайный двенадцатизначный код, который нигде на
 * сайте не публикуется и не выводится из позывного: единственный способ сюда
 * попасть — поймать передачу этого человека в эфире и взять код оттуда.
 *
 * Поэтому здесь стоит noindex и нет никакой навигации «к другим людям».
 */

require_once __DIR__ . '/lib/bootstrap.php';
efir_require_installed();

$code = (string)($_GET['u'] ?? '');
$user = ($code !== '' && is_valid_code($code)) ? find_user_by_profile_code($code) : null;

$siteName = (string)setting('site_name');

if ($user === null || (int)$user['is_blocked'] === 1) {
    http_response_code(404);
    ?>
    <!doctype html>
    <html lang="ru">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex, nofollow">
        <title>Ленты нет — <?= e($siteName) ?></title>
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
            <p>Такой ленты нет.</p>
            <p class="muted">
                Код мог быть набран с ошибкой или лента закрыта. Искать людей здесь
                негде: ни списка, ни поиска не существует, код приходит только через эфир.
            </p>
            <p><a href="/">На главную</a></p>
        </div>
    </div>
    </body>
    </html>
    <?php
    exit;
}

$userId = (int)$user['id'];
$perPage = 20;
$page = max(1, (int)($_GET['page'] ?? 1));
$total = count_user_posts($userId);
$pages = max(1, (int)ceil($total / $perPage));
$posts = user_posts($userId, $perPage, ($page - 1) * $perPage);

$handle = (string)$user['handle'];
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- Лента не должна попадать в поисковую выдачу: это обошло бы
         отсутствие поиска по людям на самом сайте. -->
    <meta name="robots" content="noindex, nofollow, noarchive">
    <title><?= e($handle) ?> — <?= e($siteName) ?></title>
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
    <div class="profile-head">
        <h1 class="profile-nick"><?= e($handle) ?></h1>
        <p class="profile-sub">
            <?= $total ?> <?= plural($total, 'запись', 'записи', 'записей') ?>
            · с <?= e(date('d.m.Y', strtotime((string)$user['created_at']))) ?>
        </p>
        <p class="profile-code">код ленты <code><?= e((string)$user['profile_code']) ?></code></p>

        <p class="profile-note">
            Личная лента. Открывается только по коду, который её хозяин передал в эфир.
            Ни списка людей, ни поиска на сайте нет — знакомятся здесь исключительно
            вживую, на расстоянии приёма.
        </p>
    </div>

    <?php $contacts = contact_rows($user); ?>
    <?php if ($contacts): ?>
        <!-- Визитку заполняет сам владелец из приложения. Пустая — не показывается
             вовсе, и это состояние по умолчанию: сеть остаётся анонимной, пока
             человек сам не решит, что ему выгоднее быть узнанным. -->
        <section class="profile-card">
            <h2 class="profile-card-title">Связаться</h2>
            <dl class="contact-list">
                <?php foreach ($contacts as $row): ?>
                    <div class="contact-row">
                        <dt><?= e($row['label']) ?></dt>
                        <dd>
                            <?php if ($row['href'] !== null): ?>
                                <a href="<?= e($row['href']) ?>" rel="nofollow noopener"
                                   <?= str_starts_with($row['href'], 'http') ? 'target="_blank"' : '' ?>>
                                    <?= e($row['text']) ?>
                                </a>
                            <?php else: ?>
                                <?= e($row['text']) ?>
                            <?php endif; ?>
                        </dd>
                    </div>
                <?php endforeach; ?>
            </dl>
            <p class="contact-note">
                Эти данные владелец ленты указал сам. Сеть их не проверяет и никому
                не раздаёт: они видны только тому, у кого есть код из эфира.
            </p>
        </section>
    <?php endif; ?>

    <?php if (!$posts): ?>
        <p class="empty">Здесь пока тихо.</p>
    <?php endif; ?>

    <ol class="profile-feed">
        <?php foreach ($posts as $row): ?>
            <?php $view = post_public_view($row); ?>
            <li class="profile-item">
                <div class="item-meta">
                    <span><?= e($view['created_human']) ?></span>
                    <span class="chip"><?= e(channel_title((int)$view['channel'])) ?></span>
                    <a href="<?= e($view['url']) ?>"><?= e($view['code']) ?></a>
                </div>
                <?php if ($view['broadcast'] !== ''): ?>
                    <h2 class="item-title"><?= e($view['broadcast']) ?></h2>
                <?php endif; ?>
                <?php if ($view['excerpt'] !== ''): ?>
                    <p class="item-excerpt"><?= e($view['excerpt']) ?></p>
                <?php endif; ?>
            </li>
        <?php endforeach; ?>
    </ol>

    <?php if ($pages > 1): ?>
        <p class="pages">
            <?php for ($i = 1; $i <= $pages; $i++): ?>
                <?php if ($i === $page): ?>
                    <strong><?= $i ?></strong>
                <?php else: ?>
                    <a href="?u=<?= e((string)$user['profile_code']) ?>&amp;page=<?= $i ?>"><?= $i ?></a>
                <?php endif; ?>
            <?php endfor; ?>
        </p>
    <?php endif; ?>
</main>

<footer class="bottom">
    <div class="bottom-inner">
        <span><?= e($siteName) ?> · <?= e((string)setting('tagline')) ?></span>
        <span>Лента открыта только по коду из эфира</span>
    </div>
</footer>

</body>
</html>
