<?php
/**
 * Общая обвязка страниц админки.
 * Использование: admin_header('Настройки'); ... admin_footer();
 *
 * @var string $adminTitle
 */
declare(strict_types=1);

function admin_header(string $title, bool $wide = false): void
{
    $siteName = (string)setting('site_name');
    ?>
    <!doctype html>
    <html lang="ru">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title><?= e($title) ?> — админка <?= e($siteName) ?></title>
        <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
        <link rel="stylesheet" href="/assets/style.css">
    </head>
    <body class="page-plain">
    <div class="admin<?= $wide ? ' admin-wide' : '' ?>">
        <div class="admin-head">
            <a class="brand" href="/">
                <span class="brand-dot"></span>
                <span class="brand-mark"><?= e($siteName) ?></span>
                <span class="brand-tag">штаб</span>
            </a>
            <nav class="admin-nav">
                <a href="/admin/">сводка</a>
                <a href="/admin/posts.php">передачи</a>
                <a href="/admin/users.php">позывные</a>
                <a href="/admin/content.php">содержимое</a>
                <a href="/admin/settings.php">настройки</a>
                <a href="/admin/logout.php">выйти</a>
            </nav>
        </div>
        <h1 class="admin-title"><?= e($title) ?></h1>
    <?php
}

function admin_footer(): void
{
    ?>
    </div>
    </body>
    </html>
    <?php
}
