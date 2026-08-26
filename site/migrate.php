<?php
declare(strict_types=1);

/**
 * Догоняет схему базы до текущей версии.
 *
 * Оставлен для совместимости и для тех, у кого есть консоль: всё то же самое
 * делает update.php, который вдобавок убирает файлы прежних версий. Сами шаги
 * живут в lib/migrations.php, чтобы не расходиться между двумя скриптами.
 *
 * Из браузера — /migrate.php под администратором, из консоли — php migrate.php.
 */

require_once __DIR__ . '/lib/bootstrap.php';

$isCli = PHP_SAPI === 'cli';

if (!efir_is_installed()) {
    exit($isCli ? "Сеть не установлена, сначала install.php\n" : 'Сеть не установлена.');
}

require_once __DIR__ . '/lib/migrations.php';

if (!$isCli && !admin_is_logged_in()) {
    header('Location: /admin/');
    exit;
}

$log = [];
$error = null;

try {
    $log = run_migrations();
    if (!$log) {
        $log[] = 'менять было нечего — схема уже в порядке';
    }
} catch (Throwable $e) {
    $error = $e->getMessage();
}

if ($isCli) {
    if ($error !== null) {
        echo 'ОШИБКА: ', $error, PHP_EOL;
        exit(1);
    }
    foreach ($log as $line) {
        echo $line, PHP_EOL;
    }
    echo 'готово', PHP_EOL;
    exit;
}
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Миграция — <?= e((string)setting('site_name')) ?></title>
    <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body class="page-plain">
<div class="install">
    <span class="brand-mark"><?= e((string)setting('site_name')) ?></span>
    <p class="muted">Схема базы</p>

    <?php if ($error !== null): ?>
        <div class="notice notice-error"><p><?= e($error) ?></p></div>
    <?php else: ?>
        <div class="notice notice-ok">
            <?php foreach ($log as $line): ?>
                <p>· <?= e($line) ?></p>
            <?php endforeach; ?>
        </div>
        <div class="notice">
            <p class="muted">
                Полное обновление, включая уборку файлов прежних версий, делает
                <a href="/update.php">update.php</a>.
            </p>
        </div>
    <?php endif; ?>

    <p><a href="/admin/">← в штаб</a></p>
</div>
</body>
</html>
