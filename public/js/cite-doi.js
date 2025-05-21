
function showCopyMessage() {
  console.log("Copied info to clipboard...");
}

function legacyCopy(text) {
  console.log( 'Using legacy copy method...' );
  // Create a temporary textarea element.
  const textarea = document.createElement( 'textarea' );
  textarea.value = text;

  // Make it non-editable to avoid focus and ensure it's not visible.
  textarea.setAttribute( 'readonly', '' );
  textarea.style.position = 'absolute';
  textarea.style.left     = '-9999px';

  document.body.appendChild( textarea );

  // Check if the user is on iOS.
  const isIOS = navigator.userAgent.match( /ipad|iphone/i );

  if (isIOS) {
    // iOS doesn't allow programmatic selection normally.
    // Create a selectable range.
    const range = document.createRange();
    range.selectNodeContents( textarea );

    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange( range );
    textarea.setSelectionRange( 0, 999999 );
  } else {
    // Select the text for other devices.
    textarea.select();
  }

  try {
    // Execute copy command.
    const successful = document.execCommand( 'copy' );

    if (successful) {
      showCopyMessage();
    } else {
      console.error( 'Copy command failed' );
    }
  } catch (err) {
    console.error( 'Error during copy: ', err );
  }

  // Clean up.
  document.body.removeChild( textarea );
}

function copyToClipboard(text) {
  // Try using the Clipboard API (modern browsers).
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText( text )
        .then( showCopyMessage )
        .catch( ()	=> legacyCopy( text ) );
  } else {
    console.log( navigator.clipboard, window.isSecureContext );
    // Use legacy method for older browsers or non-secure contexts.
    legacyCopy( text );
  }

  return true;
}

// Function to update the citation text based on selected format
function getCitationText(cite, format) {
  try {
    switch (format) {
      case 'apa':
        return cite.format('bibliography', {
          format: 'text',
          template: 'apa'
        });
      case 'mla':
        return cite.format('bibliography', {
          format: 'text',
          template: 'mla'
        });
      case 'chicago':
        return cite.format('bibliography', {
          format: 'text',
          template: 'chicago'
        });
      case 'bibtex':
        return cite.format('bibtex');
      case 'ris':
        return cite.format('ris');
      case 'harvard':
        return cite.format('bibliography', {
          format: 'text',
          template: 'harvard1'
        });
    }

    return '';
  } catch (e) {
    console.error('Error formatting citation:', e);
    citationText.text('Error formatting citation');
  }
}

function interceptXHRUrl(originalUrl, replacementUrl, once = false) {
  const originalOpen = XMLHttpRequest.prototype.open;
  let intercepted    = false;

  XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
    if ( ! intercepted && typeof url === 'string' && url.includes( originalUrl )) {
      console.log( `Intercepted XHR to ${url}` );
      const newUrl = url.replace( originalUrl, replacementUrl );
      console.log( `Redirecting to ${newUrl}` );

      if (once) {
        intercepted = true;
        setTimeout( restoreOriginal, 0 );
      }
      return originalOpen.call( this, method, newUrl, async, user, password );
    }
    return originalOpen.apply( this, arguments );
  };

  function restoreOriginal() {
    XMLHttpRequest.prototype.open = originalOpen;
    console.log( 'Restored original XHR behavior' );
    return true;
  }

  return restoreOriginal;
}



document.onreadystatechange = function () {
  if (document.readyState === "complete") {

    if (!productionDOI) {
      interceptXHRUrl('https://doi.org/', 'https://api.test.datacite.org/dois/', true);
    }
    const cite = new Cite(doi);
    const citationText = document.getElementById('citation-text');
    const citeButton = document.getElementById('copy-citation');
    const citationFormatSelector = document.getElementById('citation-format-selector');
    const citationFormat = citationFormatSelector.value;
    const citationCopied = document.getElementById('citation-copied');
    citationText.textContent = getCitationText(cite, citationFormat);

    // Event listener for format selector change
    citationFormatSelector.addEventListener('change', function () {
      const selectedFormat = citationFormatSelector.value;
      citationText.textContent = getCitationText(cite, selectedFormat);
    });

    citeButton.addEventListener('click', function (event) {
      event.preventDefault();
      copyToClipboard(citationText.textContent);
      citationCopied.classList.add('flash-active');
      setTimeout(function() {
        citationCopied.classList.remove('flash-active');
      }, 1000);
    });
  }
};
