package org.ajoberstar.grgit.auth

import org.ajoberstar.grgit.Credentials
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Person
import org.ajoberstar.grgit.fixtures.GitTestUtil
import org.ajoberstar.grgit.fixtures.LinuxSpecific

import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder

import spock.lang.Specification
import spock.lang.Unroll

@Category(LinuxSpecific)
class LinuxAuthenticationSpec extends Specification {
  private static final String SSH_URI = 'git@github.com:ajoberstar/grgit-test.git'
  private static final String HTTPS_URI = 'https://github.com/ajoberstar/grgit-test.git'

  static Credentials hardcodedCreds
  static Credentials partialCreds

  @Rule TemporaryFolder tempDir = new TemporaryFolder()

  Grgit grgit
  Person person = new Person('Bruce Wayne', 'bruce.wayne@wayneindustries.com')

  def cleanup() {
    System.properties.remove(AuthConfig.FORCE_OPTION)
    System.properties.remove(AuthConfig.USERNAME_OPTION)
    System.properties.remove(AuthConfig.PASSWORD_OPTION)
  }

  @Unroll('#method works using ssh? (#ssh) and creds? (#creds)')
  def 'auth method works'() {
    given:
    assert System.properties[AuthConfig.FORCE_OPTION] == null, 'Force should not already be set.'
    System.properties[AuthConfig.FORCE_OPTION] = method
    ready(ssh)
    assert grgit.branch.status(branch: 'master').aheadCount == 1
    when:
    grgit.push()
    then:
    grgit.branch.status(branch: 'master').aheadCount == 0
    where:
    method		    | ssh   | creds
    'hardcoded'   | false | hardcodedCreds
    'hardcoded'   | false | partialCreds
    'interactive' | false | null
    'interactive' | true  | null
    'sshagent'	  | true  | null
    'command'     | true  | null
  }

  private void ready(boolean ssh) {
    File repoDir = tempDir.newFolder('repo')
    String uri = ssh ? SSH_URI : HTTPS_URI
    grgit = Grgit.clone(uri: uri, dir: repoDir)
    grgit.repository.jgit.repo.config.with {
      setString('user', null, 'name', person.name)
      setString('user', null, 'email', person.email)
      save()
    }

    repoFile('linux_test.txt') << "${new Date().format('yyyy-MM-dd')}\n"
    grgit.add(patterns: ['.'])
    grgit.commit(message: 'Linux auth test')
  }

  private File repoFile(String path, boolean makeDirs = true) {
    return GitTestUtil.repoFile(grgit, path, makeDirs)
  }
}
