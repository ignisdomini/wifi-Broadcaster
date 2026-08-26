<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/bootstrap.php';
efir_require_installed();

admin_logout();
header('Location: /admin/');
exit;
