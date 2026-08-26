<?php
declare(strict_types=1);

/**
 * Приложение спрашивает этот адрес при запуске: лимиты задаёт админка сайта,
 * а не сборка APK, чтобы их можно было менять без перевыпуска приложения.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

json_response([
    'ok' => true,
    'site' => [
        'name'     => (string)setting('site_name'),
        'tagline'  => (string)setting('tagline'),
        'base_url' => base_url(),
        // Шаблон короткой ссылки: приложение подставляет код и получает адрес.
        'link_template' => base_url() . 'p/{code}',
    ],
    'limits' => public_limits(),
    'channels' => public_channels(),
    'require_token' => trim((string)setting('api_token', '')) !== '',
    'moderation'    => setting_int('moderation') === 1,
    'version'       => EFIR_VERSION,
]);
